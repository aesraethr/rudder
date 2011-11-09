/*
*************************************************************************************
* Copyright 2011 Normation SAS
*************************************************************************************
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as
* published by the Free Software Foundation, either version 3 of the
* License, or (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU Affero GPL v3, the copyright holders add the following
* Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU Affero GPL v3
* licence, when you create a Related Module, this Related Module is
* not considered as a part of the work and may be distributed under the
* license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Affero General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/agpl.html>.
*
*************************************************************************************
*/

package bootstrap.liftweb

import scala.xml._
import net.liftweb.util._
import net.liftweb.http._
import net.liftweb.common._
import net.liftweb.sitemap._
import net.liftweb.sitemap.Loc._
import Helpers._
import net.liftweb.http.js.jquery.JQuery14Artifacts
import net.liftweb.widgets.autocomplete.AutoComplete
import javax.servlet.UnavailableException
import LiftSpringApplicationContext.inject
import com.normation.plugins.RudderPluginDef
import com.normation.rudder.repository.EventLogRepository
import com.normation.rudder.domain.log.ApplicationStarted

/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot extends Loggable {
  def boot {
    
    ////////// bootstraps checks //////////
    val checks = inject[BootstrapChecks]("allChecks")
    checks.checks()
    
    LiftRules.early.append( {req: provider.HTTPRequest => req.setCharacterEncoding("UTF-8")})
    LiftRules.jsArtifacts = JQuery14Artifacts
    LiftRules.ajaxStart = Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)
    LiftRules.ajaxEnd = Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)
    LiftRules.ajaxPostTimeout = 30000

    // We don't want to reload the page 
    LiftRules.redirectAjaxOnSessionLoss = false;
    //we don't want to retry on ajax timeout, as it may have big consequence
    //when it's (for example) a deploy
    LiftRules.ajaxRetryCount = Full(1)
    
    // where to search snippet
    LiftRules.addToPackages("com.normation.rudder.web")

    LiftRules.statefulRewrite.append {
      //if no policy server if configured, force to configure one
//      case RewriteRequest(path,_,_) if(RudderContext.rootServerNotDefined && (path match { 
//        case ParsePath("secure"::"assetManager"::"policyServers"::Nil, _, _, _) => false 
//        case _ => true
//      })) => RewriteResponse("secure"::"assetManager"::"policyServers"::Nil)
      case RewriteRequest(ParsePath("secure" :: "configurationManager" :: "policyTemplateLibraryManagement" :: ptId :: Nil, _, _, _), GetRequest, _) =>
        RewriteResponse("secure" :: "configurationManager" :: "policyTemplateLibraryManagement" :: Nil, Map("policyTemplateId" -> ptId))
      case RewriteRequest(ParsePath("secure"::"assetManager"::"searchServers"::nodeId::Nil, _, _, _), GetRequest, _) =>
        RewriteResponse("secure"::"assetManager"::"searchServers"::Nil, Map("nodeId" -> nodeId))
    }
    
    // Fix relative path to css resources
    LiftRules.fixCSS("style" :: "style" :: Nil, Empty)
    
    
    // i18n
    LiftRules.resourceNames = "default" :: "ldapObjectAndAttributes" :: Nil
    
    
    // Content type things : use text/html in place of application/xhtml+xml
    LiftRules.useXhtmlMimeType = false
    
    /*
     * For development, we override the default local calculator
     * to allow explicit locale switch with just the addition
     * of &locale=en at the end of urls
     */
    import net.liftweb.http.provider.HTTPRequest
    import java.util.Locale
    val DefaultLocale = new Locale("")
    LiftRules.localeCalculator = {(request : Box[HTTPRequest]) => {
      request match {
        case Empty | Failure(_,_,_) => DefaultLocale
        case Full(r) => r.param("locale") match {
          case Nil => DefaultLocale
          case loc::tail => {
            logger.debug("Switch to locale: " + loc)
            loc.split("_").toList match {
              case Nil => DefaultLocale
              case lang::Nil => new Locale(lang)
              case lang::country::tail => new Locale(lang,country)
            }
          }
        }
      }
    }}

    
    //init autocomplete widget
    AutoComplete.init()
    
    
    // All the following is related to the sitemap
    
      
    val assetManagerMenu = 
      Menu("AssetManagerHome", <span>Asset Management</span>)    / "secure" / "assetManager" / "index" submenus(
          
          Menu("SearchServers", <span>Search nodes</span>)       / "secure" / "assetManager" / "searchServers" >> LocGroup("nodeGroup") 
        
        , Menu("ManageNewServer", <span>Accept new nodes</span>) / "secure" / "assetManager" / "manageNewServer" >>  LocGroup("nodeGroup")
          
        , Menu("Groups", <span>Groups</span>)                    / "secure" / "assetManager" / "groups" >> LocGroup("groupGroup") 
        
        //Menu(Loc("PolicyServers", List("secure", "assetManager","policyServers"), <span>Rudder server</span>,  LocGroup("assetGroup"))) ::
        //Menu(Loc("UploadedFiles", List("secure", "assetManager","uploadedFiles"), <span>Manage uploaded files</span>, LocGroup("filesGroup"))) ::
      )        

    def buildManagerMenu(name:String) = 
      Menu(name+"ManagerHome", <span>{name.capitalize} Management</span>) / "secure" / (name+"Manager") / "index" submenus(
          
          Menu(name+"ConfigurationRuleManagement", <span>Configuration Rules</span>) / 
            "secure" / (name+"Manager") / "configurationRuleManagement" >> LocGroup(name+"Group")
            
        , Menu(name+"PolicyInstanceManagement", <span>Policy Instances</span>) / 
            "secure" / (name+"Manager") / "policyInstanceManagement" >> LocGroup(name+"Group")
            
        , Menu("configurationPolicyTemplateLibraryManagement", <span>Policy Templates</span>) /
            "secure" / (name+"Manager") / "policyTemplateLibraryManagement" >>  LocGroup(name+"Group")
      )
      
      
    def administrationMenu = 
      Menu("AdministrationHome", <span>Administration</span>) / "secure" / "administration" /"index" submenus(
          
          Menu("eventLogViewer", <span>Event Logs</span>) / 
            "secure" / "administration" / "eventLogs" >> LocGroup("administrationGroup")
            
        , Menu("policyServerManagement", <span>Policy Server</span>) / 
            "secure" / "administration" / "policyServerManagement" >> LocGroup("administrationGroup")
            
        , Menu("pluginManagement", <span>Plugins</span>) / 
            "secure" / "administration" / "pluginManagement" >> LocGroup("administrationGroup") 
            
        , Menu("archivesManagement", <span>Archives</span>) / 
            "secure" / "administration" / "policyArchives" >> LocGroup("administrationGroup")
      )
  
    
    val rootMenu = List(
        Menu("Home", <span>Home</span>) / "secure" / "index"
      , Menu("Login") / "index" >> Hidden
      , assetManagerMenu 
      , buildManagerMenu("configuration") 
      , administrationMenu 
    ).map( _.toMenu )

    
    ////////// import and init modules //////////
    val newSiteMap = initPlugins(rootMenu.map( _.toMenu ))
    
    
    //not sur why we are using that ?
    SiteMap.enforceUniqueLinks = false

    LiftRules.setSiteMapFunc(() => SiteMap(newSiteMap:_*))

    inject[EventLogRepository].saveEventLog(ApplicationStarted())
  }
  
  private[this] def initPlugins(menus:List[Menu]) : List[Menu] = {
    
    //LiftSpringApplicationContext.springContext.refresh
    import scala.collection.JavaConversions._
    val pluginDefs = LiftSpringApplicationContext.springContext.getBeansOfType(classOf[RudderPluginDef]).values
      
    pluginDefs.foreach { plugin =>
      initPlugin(plugin)
    }
    
    //return the updated siteMap
    (menus /: pluginDefs){ case (prev, mutator) => mutator.updateSiteMap(prev) }
  }
  
  private[this] def initPlugin[T <: RudderPluginDef](plugin:T) : Unit = {
    
    logger.debug("TODO: manage one time initialization for plugin: " + plugin.id)
    logger.info("Initializing plugin '%s' [%s]".format(plugin.name.value, plugin.id))
    plugin.init
    //add the plugin packages to Lift package to look for packages
    LiftRules.addToPackages(plugin.basePackage)
  }
  
}
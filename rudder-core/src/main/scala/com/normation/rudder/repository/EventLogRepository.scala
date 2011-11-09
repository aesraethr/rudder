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

package com.normation.rudder.repository

import net.liftweb.common._
import com.normation.eventlog._
import java.security.Principal
import com.normation.rudder.domain.policies.AddConfigurationRuleDiff
import com.normation.rudder.domain.policies.ModifyConfigurationRuleDiff
import com.normation.rudder.domain.policies.DeleteConfigurationRuleDiff
import com.normation.rudder.services.log.EventLogFactory

trait EventLogRepository {
  def eventLogFactory : EventLogFactory
  
  
	/**
	 * Save an eventLog
	 * Optionnal : the user. At least one of the eventLog user or user must be defined
	 * Return the unspecialized event log with its serialization number
	 */
	def saveEventLog(eventLog : EventLog) : Box[EventLog]
	                       
  def saveAddConfigurationRule(principal : EventActor, addDiff:AddConfigurationRuleDiff) = {
    saveEventLog(eventLogFactory.getAddConfigurationRuleFromDiff(
        principal = principal
      , addDiff   = addDiff
    ))
  }
  
  def saveDeleteConfigurationRule(principal : EventActor, deleteDiff:DeleteConfigurationRuleDiff) = {
    saveEventLog(eventLogFactory.getDeleteConfigurationRuleFromDiff(
        principal  = principal
      , deleteDiff = deleteDiff
    ))
  }

  def saveModifyConfigurationRule(principal : EventActor, modifyDiff:ModifyConfigurationRuleDiff) = {
    saveEventLog(eventLogFactory.getModifyConfigurationRuleFromDiff(
        principal = principal
      , modifyDiff = modifyDiff
    ))
  }

	
	/**
	 * Get an EventLog by its entry
	 */
	def getEventLog(id : Int) : Box[EventLog]
	
	/**
	 * Returns eventlog matching criteria
	 * For the moment it only a string, it should be something else in the future
	 */
	def getEventLogByCriteria(criteria : Option[String], limit:Option[Int] = None, orderBy:Option[String] = None) : Box[Seq[EventLog]]
		
}
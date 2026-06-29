/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org.
 */

package org.openlmis.report.exception;

import org.openlmis.report.utils.Message;

/**
 * Exception representing a server-side failure that is safe to surface to the client
 * as a localized message but should map to an HTTP 500 status.
 */
public class ServerException extends BaseMessageException {

  public ServerException(Message message) {
    super(message);
  }

  public ServerException(Message message, Throwable cause) {
    super(message, cause);
  }
}

//=============================================================================
// Brief     : TFTP Action Enum
// Author(s) : Carlos Guimarães <carlos.em.guimaraes@gmail.com>
// ----------------------------------------------------------------------------
// ssTFTP - Super Simple Trivial File Transfer Protocol
//
// Copyright (C) 2008-2026 Carlos Guimarães
//
// This file is part of ssTFTP.
//
// ssTFTP is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// ssTFTP is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with ssTFTP. If not, write to the Free Software Foundation,
// Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
//=============================================================================

package pt.cguimaraes.sstftp;

/**
 * Enumeration of TFTP actions (GET or PUT).
 */
public enum TFTPAction {
    GET("get"),
    PUT("put");

    private final String value;

    TFTPAction(String value) {
        this.value = value;
    }

    /**
     * Gets the string value of this action.
     *
     * @return the string representation
     */
    public String getValue() {
        return value;
    }

    /**
     * Parses a string value to the corresponding TFTPAction.
     *
     * @param value the string value
     * @return the corresponding TFTPAction
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static TFTPAction fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Action cannot be null");
        }
        for (TFTPAction action : TFTPAction.values()) {
            if (action.value.equalsIgnoreCase(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown action: " + value);
    }
}

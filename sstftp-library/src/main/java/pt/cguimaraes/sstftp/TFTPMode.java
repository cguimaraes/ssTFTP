//=============================================================================
// Brief     : TFTP Mode Enum
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
 * Enumeration of TFTP transfer modes.
 */
public enum TFTPMode {
    OCTET("octet"),
    NETASCII("netascii");

    private final String value;

    TFTPMode(String value) {
        this.value = value;
    }

    /**
     * Gets the string value of this mode.
     *
     * @return the string representation
     */
    public String getValue() {
        return value;
    }

    /**
     * Parses a string value to the corresponding TFTPMode.
     *
     * @param value the string value
     * @return the corresponding TFTPMode
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static TFTPMode fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Mode cannot be null");
        }
        for (TFTPMode mode : TFTPMode.values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown mode: " + value);
    }
}

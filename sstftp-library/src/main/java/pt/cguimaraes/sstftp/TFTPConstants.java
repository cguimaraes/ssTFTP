//=============================================================================
// Brief     : TFTP Constants
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
 * Constants used throughout the TFTP implementation.
 */
public final class TFTPConstants {

    // Network and socket configuration
    /** Default MTU (Maximum Transmission Unit) in bytes */
    public static final int DEFAULT_MTU = 1500;

    /** Default block size in bytes */
    public static final int DEFAULT_BLOCK_SIZE = 512;

    /** Maximum allowed block size in bytes */
    public static final int MAX_BLOCK_SIZE = 65464;

    // Timeout and retry configuration
    /** Default timeout for socket operations in milliseconds */
    public static final int DEFAULT_TIMEOUT_MS = 2000;

    /** Default number of retries for message transmission */
    public static final int DEFAULT_RETRIES = 3;

    // TFTP Transfer modes
    /** TFTP octet (binary) transfer mode */
    public static final String MODE_OCTET = "octet";

    /** TFTP netascii (text) transfer mode */
    public static final String MODE_NETASCII = "netascii";

    // TFTP Option names
    /** Block size option name */
    public static final String OPTION_BLOCKSIZE = "blksize";

    /** Timeout interval option name */
    public static final String OPTION_TIMEOUT = "timeout";

    /** Transfer size option name */
    public static final String OPTION_TSIZE = "tsize";

    // Timeout interval constraints
    /** Minimum accepted timeout interval in seconds */
    public static final int MIN_TIMEOUT_INTERVAL = 1;

    /** Maximum accepted timeout interval in seconds */
    public static final int MAX_TIMEOUT_INTERVAL = 255;

    // Bit shift and mask constants for binary operations
    /** Bit shift for high byte in 16-bit values */
    public static final int BYTE_SHIFT = 8;

    /** Byte mask for extracting individual bytes */
    public static final int BYTE_MASK = 0xFF;

    /** Block number mask for 16-bit wrap-around */
    public static final int BLOCK_NUMBER_MASK = 0xFFFF;

    private TFTPConstants() {
        // Private constructor to prevent instantiation
    }
}

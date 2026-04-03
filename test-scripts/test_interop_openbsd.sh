#!/bin/bash

#=============================================================================
# Brief     : Interoperability Test against OpenBSD tftpd
# Author(s) : Carlos Guimarães <carlos.em.guimaraes@gmail.com>
# ----------------------------------------------------------------------------
# ssTFTP - Open Trivial File Transfer Protocol
#
# Copyright (C) 2008-2023 Carlos Guimarães
#
# This file is part of ssTFTP.
#
# ssTFTP is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# ssTFTP is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with ssTFTP. If not, write to the Free Software Foundation,
# Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
#=============================================================================

set -e

# Get repository root directory (works in both local and CI/CD environments)
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

TFTP_PORT=6973
SSTFTP_SERVER_PORT=6974
OPENBSD_ROOT="/tmp/openbsd-tftpd-root"
SSTFTP_SERVER_ROOT="/tmp/sstftp-openbsd-server-root"
LOG_FILE="/tmp/openbsd-test.log"
SSTFTP_CLIENT_JAR="$REPO_ROOT/sstftp-client/target/sstftp-client-0.2.jar"
SSTFTP_SERVER_JAR="$REPO_ROOT/sstftp-server/target/sstftp-server-0.2.jar"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

cleanup() {
    echo "[INFO] Cleaning up resources..."
    # Kill any running ssTFTP server instances
    pkill -f "sstftp-server" || true
    # Stop OpenBSD tftpd container if running
    docker stop openbsd-tftpd-test 2>/dev/null || true
    docker rm openbsd-tftpd-test 2>/dev/null || true
    # Clean up directories
    rm -rf "$OPENBSD_ROOT" "$SSTFTP_SERVER_ROOT"
}

trap cleanup EXIT

log_test() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

pass_test() {
    echo -e "${GREEN}[PASS]${NC} $1" | tee -a "$LOG_FILE"
}

fail_test() {
    echo -e "${RED}[FAIL]${NC} $1" | tee -a "$LOG_FILE"
    return 1
}

log_test "========================================="
log_test "OpenBSD tftpd Interoperability Tests"
log_test "========================================="

# Setup directories
mkdir -p "$OPENBSD_ROOT" "$SSTFTP_SERVER_ROOT"
chmod 777 "$OPENBSD_ROOT" "$SSTFTP_SERVER_ROOT"

# Create test files
log_test "Creating test files..."
dd if=/dev/zero of="$OPENBSD_ROOT/test_256.bin" bs=256 count=1 2>/dev/null
dd if=/dev/zero of="$OPENBSD_ROOT/test_1024.bin" bs=1024 count=1 2>/dev/null
dd if=/dev/urandom of="$OPENBSD_ROOT/test_large.bin" bs=1024 count=10 2>/dev/null
echo "OpenBSD TFTP Test" > "$OPENBSD_ROOT/test_openbsd.txt"

# Start OpenBSD tftpd server in Docker (using tftpd-hpa as OpenBSD tftpd)
log_test "Starting OpenBSD tftpd server (tftpd-hpa based)..."
docker run -d \
    --name openbsd-tftpd-test \
    --net host \
    -v "$OPENBSD_ROOT:/tftp:Z" \
    ubuntu:22.04 \
    bash -c "set -x; apt-get update -qq && apt-get install -y tftpd-hpa && mkdir -p /tftp && chmod 777 /tftp && exec in.tftpd -v -l -s /tftp" \
    > /dev/null 2>&1

# Wait for server to be ready
log_test "Waiting for OpenBSD tftpd server to start..."
for i in {1..15}; do
    if docker ps | grep -q openbsd-tftpd-test; then
        sleep 1
    else
        log_test "ERROR: openbsd-tftpd container exited"
        docker logs openbsd-tftpd-test 2>&1 | tee -a "$LOG_FILE"
        break
    fi
done
sleep 2

# Test 1: ssTFTP Client GET from OpenBSD tftpd (octet mode)
log_test "---"
log_test "Test 1: ssTFTP Client GET from OpenBSD tftpd (octet mode)"
mkdir -p /tmp/sstftp-client-get-obsd
cd /tmp/sstftp-client-get-obsd

if java -jar "$SSTFTP_CLIENT_JAR" -a get -f test_256.bin -c localhost -p $TFTP_PORT -m octet 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f test_256.bin ]; then
        if cmp -s "$OPENBSD_ROOT/test_256.bin" test_256.bin; then
            pass_test "GET test_256.bin from OpenBSD tftpd (octet)"
        else
            fail_test "GET test_256.bin from OpenBSD: file content mismatch"
        fi
    else
        fail_test "GET test_256.bin from OpenBSD: file not downloaded"
    fi
else
    fail_test "GET test_256.bin from OpenBSD: download failed"
fi

cd - > /dev/null

# Test 2: ssTFTP Client GET from OpenBSD tftpd with blocksize option
log_test "---"
log_test "Test 2: ssTFTP Client GET from OpenBSD tftpd (blocksize 512)"
mkdir -p /tmp/sstftp-client-get-obsd-blk
cd /tmp/sstftp-client-get-obsd-blk

if java -jar "$SSTFTP_CLIENT_JAR" -a get -f test_1024.bin -c localhost -p $TFTP_PORT -b 512 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f test_1024.bin ]; then
        if cmp -s "$OPENBSD_ROOT/test_1024.bin" test_1024.bin; then
            pass_test "GET test_1024.bin from OpenBSD tftpd (blocksize 512)"
        else
            fail_test "GET test_1024.bin from OpenBSD: file content mismatch with blocksize"
        fi
    else
        fail_test "GET test_1024.bin from OpenBSD: file not downloaded with blocksize"
    fi
else
    fail_test "GET test_1024.bin from OpenBSD: download with blocksize failed"
fi

cd - > /dev/null

# Test 3: ssTFTP Client GET large file with default blocksize
log_test "---"
log_test "Test 3: ssTFTP Client GET large file from OpenBSD tftpd"
mkdir -p /tmp/sstftp-client-get-large-obsd
cd /tmp/sstftp-client-get-large-obsd

if java -jar "$SSTFTP_CLIENT_JAR" -a get -f test_large.bin -c localhost -p $TFTP_PORT -m octet 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f test_large.bin ]; then
        if cmp -s "$OPENBSD_ROOT/test_large.bin" test_large.bin; then
            pass_test "GET test_large.bin (10KB) from OpenBSD tftpd"
        else
            fail_test "GET test_large.bin: file content mismatch"
        fi
    else
        fail_test "GET test_large.bin: file not downloaded"
    fi
else
    fail_test "GET test_large.bin: download failed"
fi

cd - > /dev/null

# Test 4: ssTFTP Client PUT to OpenBSD tftpd
log_test "---"
log_test "Test 4: ssTFTP Client PUT to OpenBSD tftpd (octet mode)"
mkdir -p /tmp/sstftp-client-put-obsd
cd /tmp/sstftp-client-put-obsd
dd if=/dev/urandom of=test_put_obsd.bin bs=256 count=3 2>/dev/null

if java -jar "$SSTFTP_CLIENT_JAR" -a put -f test_put_obsd.bin -c localhost -p $TFTP_PORT -m octet 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f "$OPENBSD_ROOT/test_put_obsd.bin" ]; then
        if cmp -s test_put_obsd.bin "$OPENBSD_ROOT/test_put_obsd.bin"; then
            pass_test "PUT test_put_obsd.bin to OpenBSD tftpd (octet)"
        else
            fail_test "PUT test_put_obsd.bin: file content mismatch"
        fi
    else
        fail_test "PUT test_put_obsd.bin: file not uploaded to OpenBSD server"
    fi
else
    fail_test "PUT test_put_obsd.bin: upload to OpenBSD server failed"
fi

cd - > /dev/null

# Test 5: ssTFTP Client GET with netascii mode
log_test "---"
log_test "Test 5: ssTFTP Client GET from OpenBSD tftpd (netascii mode)"
mkdir -p /tmp/sstftp-client-get-ascii-obsd
cd /tmp/sstftp-client-get-ascii-obsd

if java -jar "$SSTFTP_CLIENT_JAR" -a get -f test_openbsd.txt -c localhost -p $TFTP_PORT -m netascii 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f test_openbsd.txt ]; then
        pass_test "GET test_openbsd.txt from OpenBSD tftpd (netascii)"
    else
        fail_test "GET test_openbsd.txt: file not downloaded with netascii"
    fi
else
    fail_test "GET test_openbsd.txt: download with netascii mode failed"
fi

cd - > /dev/null

# Test 6: Reference OpenBSD tftpd Client GET from ssTFTP Server
log_test "---"
log_test "Test 6: Reference OpenBSD Client GET from ssTFTP Server"

# Start ssTFTP server
log_test "Starting ssTFTP server..."
mkdir -p "$SSTFTP_SERVER_ROOT"
cp "$OPENBSD_ROOT/test_256.bin" "$SSTFTP_SERVER_ROOT/"
cp "$OPENBSD_ROOT/test_openbsd.txt" "$SSTFTP_SERVER_ROOT/"

cd $REPO_ROOT
java -jar "$SSTFTP_SERVER_JAR" -p $SSTFTP_SERVER_PORT -r "$SSTFTP_SERVER_ROOT" > /tmp/sstftp-server.log 2>&1 &
SSTFTP_PID=$!
sleep 3

mkdir -p /tmp/openbsd-client-get
cd /tmp/openbsd-client-get

if tftp -m octet -v localhost $SSTFTP_SERVER_PORT << 'EOF' 2>&1 | tee -a "$LOG_FILE"
get test_256.bin
quit
EOF
then
    if [ -f test_256.bin ]; then
        if cmp -s "$SSTFTP_SERVER_ROOT/test_256.bin" test_256.bin; then
            pass_test "Reference OpenBSD Client GET test_256.bin from ssTFTP Server"
        else
            fail_test "Reference OpenBSD GET test_256.bin: file content mismatch"
        fi
    else
        fail_test "Reference OpenBSD GET test_256.bin: file not downloaded"
    fi
else
    fail_test "Reference OpenBSD GET test_256.bin: download failed"
fi

cd - > /dev/null
kill $SSTFTP_PID 2>/dev/null || true
wait $SSTFTP_PID 2>/dev/null || true

# Test 7: Reference OpenBSD Client PUT to ssTFTP Server
log_test "---"
log_test "Test 7: Reference OpenBSD Client PUT to ssTFTP Server"

# Start ssTFTP server again
log_test "Starting ssTFTP server..."
cd $REPO_ROOT
java -jar "$SSTFTP_SERVER_JAR" -p $SSTFTP_SERVER_PORT -r "$SSTFTP_SERVER_ROOT" > /tmp/sstftp-server.log 2>&1 &
SSTFTP_PID=$!
sleep 3

mkdir -p /tmp/openbsd-client-put
cd /tmp/openbsd-client-put
dd if=/dev/urandom of=test_put_ref_obsd.bin bs=512 count=1 2>/dev/null

if tftp -m octet -v localhost $SSTFTP_SERVER_PORT << 'EOF' 2>&1 | tee -a "$LOG_FILE"
put test_put_ref_obsd.bin
quit
EOF
then
    if [ -f "$SSTFTP_SERVER_ROOT/test_put_ref_obsd.bin" ]; then
        if cmp -s test_put_ref_obsd.bin "$SSTFTP_SERVER_ROOT/test_put_ref_obsd.bin"; then
            pass_test "Reference OpenBSD Client PUT test_put_ref_obsd.bin to ssTFTP Server"
        else
            fail_test "Reference OpenBSD PUT: file content mismatch"
        fi
    else
        fail_test "Reference OpenBSD PUT: file not uploaded to ssTFTP server"
    fi
else
    fail_test "Reference OpenBSD PUT: upload to ssTFTP server failed"
fi

cd - > /dev/null
kill $SSTFTP_PID 2>/dev/null || true
wait $SSTFTP_PID 2>/dev/null || true

log_test "---"
log_test "========================================="
log_test "OpenBSD tftpd Interoperability Tests Completed"
log_test "========================================="

exit 0

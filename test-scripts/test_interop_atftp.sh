#!/bin/bash

#=============================================================================
# Brief     : Interoperability Test against atftp
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

TFTP_PORT=6971
SSTFTP_SERVER_PORT=6972
ATFTP_ROOT="/tmp/atftp-root"
SSTFTP_SERVER_ROOT="/tmp/sstftp-atftp-server-root"
ATFTP_CLIENT_GET_DIR="/tmp/atftp-client-get"
ATFTP_CLIENT_PUT_DIR="/tmp/atftp-client-put"
LOG_FILE="/tmp/atftp-test.log"
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
    # Stop atftp container if running
    docker stop atftp-test 2>/dev/null || true
    docker rm atftp-test 2>/dev/null || true
    # Clean up directories
    rm -rf "$ATFTP_ROOT" "$SSTFTP_SERVER_ROOT" "$ATFTP_CLIENT_GET_DIR" "$ATFTP_CLIENT_PUT_DIR"
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
log_test "ATFTP Interoperability Tests"
log_test "========================================="

# Setup directories
mkdir -p "$ATFTP_ROOT" "$SSTFTP_SERVER_ROOT" "$ATFTP_CLIENT_GET_DIR" "$ATFTP_CLIENT_PUT_DIR"
chmod 777 "$ATFTP_ROOT" "$SSTFTP_SERVER_ROOT" "$ATFTP_CLIENT_GET_DIR" "$ATFTP_CLIENT_PUT_DIR"

# Create test files
log_test "Creating test files..."
dd if=/dev/zero of="$ATFTP_ROOT/test_512.bin" bs=512 count=1 2>/dev/null
dd if=/dev/zero of="$ATFTP_ROOT/test_2048.bin" bs=2048 count=1 2>/dev/null
dd if=/dev/urandom of="$ATFTP_ROOT/test_random.bin" bs=256 count=4 2>/dev/null
echo "ATFTP Test File Content" > "$ATFTP_ROOT/test_atftp.txt"

# Start atftp server in Docker
log_test "Starting atftp server..."
docker run -d \
    --name atftp-test \
    --net host \
    -v "$ATFTP_ROOT:/tftp:Z" \
    -v "$ATFTP_CLIENT_GET_DIR:/client-get:Z" \
    -v "$ATFTP_CLIENT_PUT_DIR:/client-put:Z" \
    ubuntu:22.04 \
    bash -c "set -x; apt-get update -qq && apt-get install -y atftpd atftp ; mkdir -p /tftp ; chmod 777 /tftp ; atftpd --daemon --port $TFTP_PORT -v /tftp ; sleep infinity" \
    > /dev/null 2>&1

# Wait for atftp to be ready
log_test "Waiting for atftp server to start..."
for i in {1..15}; do
    if docker ps | grep -q atftp-test; then
        sleep 1
    else
        log_test "ERROR: atftp container exited"
        docker logs atftp-test 2>&1 | tee -a "$LOG_FILE"
        break
    fi
done
sleep 2

# Test 1: ssTFTP Client GET from atftp Server (octet mode)
log_test "---"
log_test "Test 1: ssTFTP Client GET from atftp (octet mode)"
mkdir -p /tmp/sstftp-client-get-atftp
cd /tmp/sstftp-client-get-atftp

if java -jar "$SSTFTP_CLIENT_JAR" -a get -f test_512.bin -c localhost -p $TFTP_PORT -m octet 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f test_512.bin ]; then
        if cmp -s "$ATFTP_ROOT/test_512.bin" test_512.bin; then
            pass_test "GET test_512.bin from atftp (octet)"
        else
            fail_test "GET test_512.bin from atftp: file content mismatch"
        fi
    else
        fail_test "GET test_512.bin from atftp: file not downloaded"
    fi
else
    fail_test "GET test_512.bin from atftp: download failed"
fi

cd - > /dev/null

# Test 2: ssTFTP Client GET from atftp Server with blocksize option
log_test "---"
log_test "Test 2: ssTFTP Client GET from atftp (blocksize 128)"
mkdir -p /tmp/sstftp-client-get-atftp-blk
cd /tmp/sstftp-client-get-atftp-blk

if java -jar "$SSTFTP_CLIENT_JAR" -a get -f test_2048.bin -c localhost -p $TFTP_PORT -b 128 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f test_2048.bin ]; then
        if cmp -s "$ATFTP_ROOT/test_2048.bin" test_2048.bin; then
            pass_test "GET test_2048.bin from atftp (blocksize 128)"
        else
            fail_test "GET test_2048.bin from atftp: file content mismatch with blocksize"
        fi
    else
        fail_test "GET test_2048.bin from atftp: file not downloaded with blocksize"
    fi
else
    fail_test "GET test_2048.bin from atftp: download with blocksize failed"
fi

cd - > /dev/null

# Test 3: ssTFTP Client PUT to atftp Server
log_test "---"
log_test "Test 3: ssTFTP Client PUT to atftp (octet mode)"
mkdir -p /tmp/sstftp-client-put-atftp
cd /tmp/sstftp-client-put-atftp
dd if=/dev/urandom of=test_put_atftp.bin bs=512 count=2 2>/dev/null

if java -jar "$SSTFTP_CLIENT_JAR" -a put -f test_put_atftp.bin -c localhost -p $TFTP_PORT -m octet 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f "$ATFTP_ROOT/test_put_atftp.bin" ]; then
        if cmp -s test_put_atftp.bin "$ATFTP_ROOT/test_put_atftp.bin"; then
            pass_test "PUT test_put_atftp.bin to atftp (octet)"
        else
            fail_test "PUT test_put_atftp.bin: file content mismatch"
        fi
    else
        fail_test "PUT test_put_atftp.bin: file not uploaded to atftp server"
    fi
else
    fail_test "PUT test_put_atftp.bin: upload to atftp failed"
fi

cd - > /dev/null

# Test 4: ssTFTP Client GET with netascii mode
log_test "---"
log_test "Test 4: ssTFTP Client GET from atftp (netascii mode)"
mkdir -p /tmp/sstftp-client-get-ascii-atftp
cd /tmp/sstftp-client-get-ascii-atftp

if java -jar "$SSTFTP_CLIENT_JAR" -a get -f test_atftp.txt -c localhost -p $TFTP_PORT -m netascii 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f test_atftp.txt ]; then
        pass_test "GET test_atftp.txt from atftp (netascii)"
    else
        fail_test "GET test_atftp.txt: file not downloaded with netascii"
    fi
else
    fail_test "GET test_atftp.txt: download with netascii mode failed"
fi

cd - > /dev/null

# Test 5: Reference atftp Client GET from ssTFTP Server
log_test "---"
log_test "Test 5: Reference atftp Client GET from ssTFTP Server"

# Start ssTFTP server
log_test "Starting ssTFTP server..."
mkdir -p "$SSTFTP_SERVER_ROOT"
cp "$ATFTP_ROOT/test_512.bin" "$SSTFTP_SERVER_ROOT/"
cp "$ATFTP_ROOT/test_atftp.txt" "$SSTFTP_SERVER_ROOT/"

cd $REPO_ROOT
java -jar "$SSTFTP_SERVER_JAR" -p $SSTFTP_SERVER_PORT -d "$SSTFTP_SERVER_ROOT" > /tmp/sstftp-server.log 2>&1 &
SSTFTP_PID=$!
sleep 3

if docker exec atftp-test atftp --get --remote-file test_512.bin --local-file /client-get/test_512.bin localhost $SSTFTP_SERVER_PORT 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f "$ATFTP_CLIENT_GET_DIR/test_512.bin" ]; then
        if cmp -s "$SSTFTP_SERVER_ROOT/test_512.bin" "$ATFTP_CLIENT_GET_DIR/test_512.bin"; then
            pass_test "Reference atftp Client GET test_512.bin from ssTFTP Server"
        else
            fail_test "Reference atftp GET test_512.bin: file content mismatch"
        fi
    else
        fail_test "Reference atftp GET test_512.bin: file not downloaded"
    fi
else
    fail_test "Reference atftp GET test_512.bin: download failed"
fi

kill $SSTFTP_PID 2>/dev/null || true
wait $SSTFTP_PID 2>/dev/null || true

# Test 6: Reference atftp Client PUT to ssTFTP Server
log_test "---"
log_test "Test 6: Reference atftp Client PUT to ssTFTP Server"

# Start ssTFTP server again
log_test "Starting ssTFTP server..."
cd $REPO_ROOT
java -jar "$SSTFTP_SERVER_JAR" -p $SSTFTP_SERVER_PORT -d "$SSTFTP_SERVER_ROOT" > /tmp/sstftp-server.log 2>&1 &
SSTFTP_PID=$!
sleep 3

# Create test file in the client-put directory
dd if=/dev/urandom of="$ATFTP_CLIENT_PUT_DIR/test_put_ref_atftp.bin" bs=256 count=2 2>/dev/null

if docker exec atftp-test atftp --put --local-file /client-put/test_put_ref_atftp.bin --remote-file test_put_ref_atftp.bin localhost $SSTFTP_SERVER_PORT 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f "$SSTFTP_SERVER_ROOT/test_put_ref_atftp.bin" ]; then
        if cmp -s "$ATFTP_CLIENT_PUT_DIR/test_put_ref_atftp.bin" "$SSTFTP_SERVER_ROOT/test_put_ref_atftp.bin"; then
            pass_test "Reference atftp Client PUT test_put_ref_atftp.bin to ssTFTP Server"
        else
            fail_test "Reference atftp PUT: file content mismatch"
        fi
    else
        fail_test "Reference atftp PUT: file not uploaded to ssTFTP server"
    fi
else
    fail_test "Reference atftp PUT: upload to ssTFTP server failed"
fi

kill $SSTFTP_PID 2>/dev/null || true
wait $SSTFTP_PID 2>/dev/null || true

log_test "---"
log_test "========================================="
log_test "atftp Interoperability Tests Completed"
log_test "========================================="

exit 0

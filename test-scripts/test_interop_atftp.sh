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
    # Stop all atftp containers if running
    docker stop atftp-test* 2>/dev/null || true
    docker rm atftp-test* 2>/dev/null || true
    # Clean up directories
    rm -rf "$ATFTP_ROOT" "$SSTFTP_SERVER_ROOT" "$ATFTP_CLIENT_GET_DIR" "$ATFTP_CLIENT_PUT_DIR" /tmp/sstftp-client-*
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

# Helper function to start atftpd with specific options
# Usage: start_atftp_server <container_name> <port> <option_flags>
start_atftp_server() {
    local container_name="$1"
    local port="$2"
    local option_flags="$3"
    
    docker run -d \
        --name "$container_name" \
        --net host \
        -v "$ATFTP_ROOT:/tftp:Z" \
        -v "$ATFTP_CLIENT_GET_DIR:/client-get:Z" \
        -v "$ATFTP_CLIENT_PUT_DIR:/client-put:Z" \
        ubuntu:22.04 \
        bash -c "set -x; apt-get update -qq && apt-get install -y atftpd atftp ; mkdir -p /tftp ; chmod 777 /tftp ; atftpd --daemon --port $port $option_flags /tftp ; sleep infinity" \
        > /dev/null 2>&1
    
    # Wait for container to be ready
    local retries=0
    while [ $retries -lt 15 ]; do
        if docker ps | grep -q "$container_name"; then
            sleep 1
            return 0
        fi
        sleep 0.5
        retries=$((retries + 1))
    done
    
    log_test "ERROR: atftp container $container_name failed to start"
    return 1
}

# Helper function to run ssTFTP client with specific options
# Usage: run_sstftp_client <action> <file> <port> <blocksize> <disable_tsize>
run_sstftp_client() {
    local action="$1"
    local file="$2"
    local port="$3"
    local blocksize="$4"
    local disable_tsize="$5"
    
    local cmd="java -jar \"$SSTFTP_CLIENT_JAR\" -a $action -f $file -c localhost -p $port -m octet"
    
    if [ -n "$blocksize" ]; then
        cmd="$cmd -b $blocksize"
    fi
    
    if [ "$disable_tsize" = "true" ]; then
        cmd="$cmd -s"
    fi
    
    eval "$cmd"
}

# Helper function to validate file transfer
# Usage: validate_transfer <source_file> <dest_file> <test_name>
validate_transfer() {
    local source="$1"
    local dest="$2"
    local test_name="$3"
    
    if [ ! -f "$dest" ]; then
        fail_test "$test_name: file not transferred"
        return 1
    fi
    
    if ! cmp -s "$source" "$dest" 2>/dev/null; then
        fail_test "$test_name: file content mismatch"
        return 1
    fi
    
    pass_test "$test_name"
    return 0
}

# Helper function to run test with specific atftpd options
# Usage: run_option_test <test_name> <atftp_options> <client_action> <file> <blocksize> <disable_tsize>
run_option_test() {
    local test_name="$1"
    local atftp_options="$2"
    local client_action="$3"
    local file="$4"
    local blocksize="$5"
    local disable_tsize="$6"
    
    local container_name="atftp-test-$(echo "$test_name" | tr ' ' '_' | tr -cd '[:alnum:]_')"
    local test_dir="/tmp/sstftp-client-$client_action-$test_name"
    
    # Start atftpd with specific options
    if ! start_atftp_server "$container_name" $TFTP_PORT "$atftp_options"; then
        fail_test "$test_name: Failed to start atftpd"
        return 1
    fi
    
    sleep 2
    
    log_test "Running: $test_name"
    mkdir -p "$test_dir"
    cd "$test_dir"
    
    if [ "$client_action" = "put" ]; then
        # For PUT tests, create a test file first
        local put_file="test_put_${test_name// /_}.bin"
        dd if=/dev/urandom of="$put_file" bs=512 count=2 2>/dev/null
        file="$put_file"
        
        if run_sstftp_client put "$file" $TFTP_PORT "$blocksize" "$disable_tsize" 2>&1 | tee -a "$LOG_FILE"; then
            validate_transfer "$test_dir/$file" "$ATFTP_ROOT/$file" "$test_name"
        else
            fail_test "$test_name: $client_action failed"
        fi
    else
        # For GET tests
        if run_sstftp_client get "$file" $TFTP_PORT "$blocksize" "$disable_tsize" 2>&1 | tee -a "$LOG_FILE"; then
            validate_transfer "$ATFTP_ROOT/$file" "$test_dir/$file" "$test_name"
        else
            fail_test "$test_name: $client_action failed"
        fi
    fi
    
    cd - > /dev/null
    
    # Stop this instance of atftpd
    docker stop "$container_name" 2>/dev/null || true
    docker rm "$container_name" 2>/dev/null || true
    sleep 1
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

# Start default atftp server in Docker (all options enabled)
log_test "Starting default atftp server (all options enabled)..."
docker run -d \
    --name atftp-test-baseline \
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
    if docker ps | grep -q atftp-test-baseline; then
        sleep 1
    else
        log_test "ERROR: atftp container exited"
        docker logs atftp-test-baseline 2>&1 | tee -a "$LOG_FILE"
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

if docker exec atftp-test-baseline atftp --get --remote-file test_512.bin --local-file /client-get/test_512.bin localhost $SSTFTP_SERVER_PORT 2>&1 | tee -a "$LOG_FILE"; then
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

if docker exec atftp-test-baseline atftp --put --local-file /client-put/test_put_ref_atftp.bin --remote-file test_put_ref_atftp.bin localhost $SSTFTP_SERVER_PORT 2>&1 | tee -a "$LOG_FILE"; then
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

# =========================================================================
# TFTP OPTIONS TESTING SECTION
# Test matrix: disable different RFC 2348/2349/7440/2090 options in atftpd
# =========================================================================

log_test "---"
log_test "========================================="
log_test "TFTP OPTIONS COMPLIANCE TESTS"
log_test "========================================="

# Clean up baseline server
docker stop atftp-test-baseline 2>/dev/null || true
docker rm atftp-test-baseline 2>/dev/null || true
sleep 1

# Test 7: Baseline (all options enabled)
log_test "---"
log_test "Test 7: Baseline - All Options Enabled (GET with blocksize)"
run_option_test "baseline-all-enabled-get" "" "get" "test_512.bin" "256" "false"

log_test "---"
log_test "Test 8: Baseline - All Options Enabled (PUT with blocksize)"
run_option_test "baseline-all-enabled-put" "" "put" "test_512.bin" "256" "false"

# Test 9: Disable timeout option (--no-timeout)
log_test "---"
log_test "Test 9: Timeout Disabled (GET - client with default timeout)"
run_option_test "timeout-disabled-get" "--no-timeout" "get" "test_512.bin" "" "false"

log_test "---"
log_test "Test 10: Timeout Disabled (PUT - client with default timeout)"
run_option_test "timeout-disabled-put" "--no-timeout" "put" "test_512.bin" "" "false"

# Test 11: Disable tsize option (--no-tsize)
log_test "---"
log_test "Test 11: Tsize Disabled (GET - client requesting tsize)"
run_option_test "tsize-disabled-get" "--no-tsize" "get" "test_512.bin" "" "false"

log_test "---"
log_test "Test 12: Tsize Disabled (PUT - client requesting tsize)"
run_option_test "tsize-disabled-put" "--no-tsize" "put" "test_512.bin" "" "false"

log_test "---"
log_test "Test 13: Tsize Disabled (GET - client disabling tsize)"
run_option_test "tsize-disabled-client-get" "--no-tsize" "get" "test_512.bin" "" "true"

log_test "---"
log_test "Test 14: Tsize Disabled (PUT - client disabling tsize)"
run_option_test "tsize-disabled-client-put" "--no-tsize" "put" "test_512.bin" "" "true"

# Test 15: Disable blksize option (--no-blksize)
log_test "---"
log_test "Test 15: Blksize Disabled (GET - client requesting blksize)"
run_option_test "blksize-disabled-get" "--no-blksize" "get" "test_512.bin" "256" "false"

log_test "---"
log_test "Test 16: Blksize Disabled (PUT - client requesting blksize)"
run_option_test "blksize-disabled-put" "--no-blksize" "put" "test_512.bin" "256" "false"

log_test "---"
log_test "Test 17: Blksize Disabled (GET - client with default blocksize)"
run_option_test "blksize-disabled-default-get" "--no-blksize" "get" "test_512.bin" "" "false"

log_test "---"
log_test "Test 18: Blksize Disabled (PUT - client with default blocksize)"
run_option_test "blksize-disabled-default-put" "--no-blksize" "put" "test_512.bin" "" "false"

# Test 19: Disable windowsize option (--no-windowsize)
# Note: ssTFTP client doesn't support windowsize yet, should gracefully ignore
log_test "---"
log_test "Test 19: Windowsize Disabled (GET - client ignores unsupported option)"
run_option_test "windowsize-disabled-get" "--no-windowsize" "get" "test_512.bin" "" "false"

log_test "---"
log_test "Test 20: Windowsize Disabled (PUT - client ignores unsupported option)"
run_option_test "windowsize-disabled-put" "--no-windowsize" "put" "test_512.bin" "" "false"

# Test 21: Disable multicast option (--no-multicast)
# Note: ssTFTP client doesn't support multicast yet, should gracefully ignore
log_test "---"
log_test "Test 21: Multicast Disabled (GET - client ignores unsupported option)"
run_option_test "multicast-disabled-get" "--no-multicast" "get" "test_512.bin" "" "false"

log_test "---"
log_test "Test 22: Multicast Disabled (PUT - client ignores unsupported option)"
run_option_test "multicast-disabled-put" "--no-multicast" "put" "test_512.bin" "" "false"

# Test 23: Disable multiple options (--no-blksize --no-tsize)
log_test "---"
log_test "Test 23: Blksize and Tsize Disabled (GET)"
run_option_test "blksize-tsize-disabled-get" "--no-blksize --no-tsize" "get" "test_512.bin" "" "false"

log_test "---"
log_test "Test 24: Blksize and Tsize Disabled (PUT)"
run_option_test "blksize-tsize-disabled-put" "--no-blksize --no-tsize" "put" "test_512.bin" "" "false"

# Test 25: Disable multiple options (--no-timeout --no-blksize --no-tsize)
log_test "---"
log_test "Test 25: Timeout, Blksize, and Tsize Disabled (GET)"
run_option_test "timeout-blksize-tsize-disabled-get" "--no-timeout --no-blksize --no-tsize" "get" "test_512.bin" "" "false"

log_test "---"
log_test "Test 26: Timeout, Blksize, and Tsize Disabled (PUT)"
run_option_test "timeout-blksize-tsize-disabled-put" "--no-timeout --no-blksize --no-tsize" "put" "test_512.bin" "" "false"

# Test 27: Large file with blksize disabled
log_test "---"
log_test "Test 27: Large File Transfer with Blksize Disabled (GET)"
run_option_test "large-blksize-disabled-get" "--no-blksize" "get" "test_2048.bin" "" "false"

log_test "---"
log_test "Test 28: Large File Transfer with Blksize Disabled (PUT)"
run_option_test "large-blksize-disabled-put" "--no-blksize" "put" "test_2048.bin" "" "false"

# Test 29: Client requesting blocksize with all options enabled
log_test "---"
log_test "Test 29: Client Requesting Large Blocksize (GET)"
run_option_test "client-large-blocksize-get" "" "get" "test_2048.bin" "1024" "false"

log_test "---"
log_test "Test 30: Client Requesting Large Blocksize (PUT)"
run_option_test "client-large-blocksize-put" "" "put" "test_2048.bin" "1024" "false"

log_test "---"
log_test "========================================="
log_test "TFTP OPTIONS COMPLIANCE TESTS COMPLETED"
log_test "========================================="

log_test "---"
log_test "========================================="
log_test "All atftp Interoperability Tests Completed"
log_test "========================================="

exit 0

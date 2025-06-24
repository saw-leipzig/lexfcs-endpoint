#!/bin/bash

# ##########################################################################
# helper message functions
# ##########################################################################

_head() {
    msg="$1"
    echo -e "\n\033[1m[*] ${msg}\033[0m"
}
_info() {
    msg="$1"
    echo -e "[*] ${msg}"
}
_debug() {
    msg="$1"
    echo -e "\033[2m[*] ${msg}\033[0m"
}
_warn() {
    msg="$1"
    echo -e "\033[33m[!] ${msg}\033[0m"
}
_run_cmd() {
    CMD=$1
    echo -e "\033[2m>>> Run command: ${CMD}\033[0m"
    stdbuf -oL bash -c "${CMD}" 2>&1 |
    while IFS= read -r line; do
        echo -e "\033[2m### ${line}\033[0m"
    done
    CMD_STATUS="${PIPESTATUS[0]}"
    echo -e "\033[2m>>> Command status: ${CMD_STATUS}\033[0m"
    return "${CMD_STATUS}"
}
_run_cmd_array() {
    CMD=( "$@" )
    # for c in "${CMD[@]}"; do echo "$c"; done
    echo -e "\033[2m>>> Run command: ${CMD[@]}\033[0m"
    stdbuf -oL "${CMD[@]}" 2>&1 |
    while IFS= read -r line; do
        echo -e "\033[2m### ${line}\033[0m"
    done
    CMD_STATUS="${PIPESTATUS[0]}"
    echo -e "\033[2m>>> Command status: ${CMD_STATUS}\033[0m"
    return "${CMD_STATUS}"
}

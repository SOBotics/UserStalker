#!/bin/bash

VERSION="3.0.0"

SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
JAR_FILE="${SCRIPT_DIR}/userstalker-${VERSION}.jar"
LOG_FILE="${SCRIPT_DIR}/userstalker.log"

while true;
do
   {
   printf '\n'
   printf '=%.0s' {1..80}
   printf "\nStarted v${VERSION} @ %s\n" "$(date)"
   printf '=%.0s' {1..80}
   printf '\n'
   } >> "${LOG_FILE}"

   cd "${SCRIPT_DIR}" || exit
   nohup java -jar "${JAR_FILE}" >> "${LOG_FILE}" 2>&1 &
done

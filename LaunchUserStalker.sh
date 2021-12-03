#!/bin/bash

SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
JAR_FILE="${SCRIPT_DIR}/UserStalker.jar"
LOG_FILE="${SCRIPT_DIR}/UserStalker.log"
GIT_CLONE_URL="https://github.com/SOBotics/UserStalker.git"

while true;
do
	{
	printf '\n'
	printf '=%.0s' {1..80}
	printf '\n'
	} >> "${LOG_FILE}"

	cd "${SCRIPT_DIR}" || exit
	java -jar "${JAR_FILE}" >> "${LOG_FILE}" 2>&1
	if [ $? -eq 42 ]; then
		# Do in-place upgrade.
		mkdir -p "temp"
		rm -rf 'temp/{*,.*}'
		cd "temp" || exit
		git clone "${GIT_CLONE_URL}"
		cd "UserStalker" || exit
		mvn package
		cp "target/UserStalker.jar" "${SCRIPT_DIR}"
		cp "LaunchUserStalker.sh" "${SCRIPT_DIR}"
		chmod +x "../LaunchUserStalker.sh"
		# NOTE: Do not copy .properties files!
	fi
done

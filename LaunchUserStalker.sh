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
	} >> "${LOG_FILE}" 2>&1

	cd "${SCRIPT_DIR}" || exit
	java -jar "${JAR_FILE}" "${1}" >> "${LOG_FILE}" 2>&1
	if [ $? -eq 42 ]; then
		# Do in-place upgrade.
		{
			printf '\n'
			printf '\055%.0s' {1..80}
			printf "\nStart of in-place upgrade attempt.\n"
			printf '\055%.0s' {1..80}
			printf '\n'

			rm -rf 'temp'
			mkdir -p "temp"
			cd "temp" || exit
			git clone "${GIT_CLONE_URL}" || exit
			cd "UserStalker" || exit
			mvn package || exit
			cp "target/UserStalker-"*".jar" "${SCRIPT_DIR}/UserStalker.jar"
			cp "LaunchUserStalker.sh" "${SCRIPT_DIR}"
			chmod +x "${SCRIPT_DIR}/LaunchUserStalker.sh"
			cp "properties/"*[^login].properties "${SCRIPT_DIR}/properties/" # do not copy "login.properties" file!

			printf '\n'
			printf '\055%.0s' {1..80}
			printf "\nEnd of in-place upgrade attempt.\n"
			printf '\055%.0s' {1..80}
			printf '\n'

			sudo reboot
			break
		} >> "${LOG_FILE}" 2>&1
	fi
done

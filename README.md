# Herodotus: A Slack historian

Your friendly neighbourhood Slack historian. Archives Slack data (especially messages) to a  database.

## Installation

* Configuration should be in a file called `config.edn` containing the following fields:

* `api-port` (the TCP port to listen on for connections)
* `webhook-url` (The Slack provided URL for the base bot channel, e.g. herodotus)
* `token` (API token provided by Slack on installation)
* `repl-username` (The username for the remote repl)
* `repl-password` (The password for the remote repl)
* `beginning-of-history` (From when to start achiving messages)

## Usage

FIXME: explanation

    $ java -jar slack-downloader-0.1.0-standalone.jar [args]

## License

Copyright © 2019 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.

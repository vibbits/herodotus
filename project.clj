(defproject slack-downloader "0.1.0-SNAPSHOT"
  :description "Slack bot. Create archives of your workspace chat history."
  :url "https://github.com/"
  :license {:name "GPL-3.0-or-later"
            :url "https://www.gnu.org/licenses/gpl-3.0-standalone.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [environ/environ.core "0.3.1"]
                 [clojure.java-time "0.3.2"]
                 [toucan "1.15.0"]
                 [org.postgresql/postgresql "42.2.8"]
                 [com.github.seratch/jslack-api-client "3.1.0"]
                 [org.slf4j/slf4j-simple "1.7.21"]]
  :main ^:skip-aot slack-downloader.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

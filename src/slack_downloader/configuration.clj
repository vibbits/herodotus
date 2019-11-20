(ns slack-downloader.configuration
  (:require
   [clojure.edn :as edn]
   [environ.core :refer [env]]
   [slack-downloader.storage :refer [storage-init]]))

(def api-port (atom 8990))        ;; Web server API port
(def webhook-url (atom ""))       ;; The URL to post messagese to #herodotus
(def token (atom ""))             ;; Slack security token
(def repl-username (atom "repl")) ;; Username for authentication to the remote repl
(def repl-password (atom "repl")) ;; Password for authentication to the remote repl

(defn config-from-env
  "Load configuration from environment variables."
  []
  {:api-port (or (env :api-port) 80)
   :webhook-url (or (env :webhook-url) "")
   :token (or (env :token) "")
   :repl-username (or (env :repl-user) "repl")
   :repl-password (or (env :repl-pass) "repl")})

(defn config
  "Set up application configuration."
  []
  (storage-init)
  (let [app-config (merge (config-from-env)
                          (edn/read-string
                           (try (slurp "config.edn")
                                (catch Throwable e "{}"))))]
    (reset! api-port (get app-config :api-port))
    (reset! webhook-url (get app-config :webhook-url))
    (reset! token (get app-config :token))
    (reset! repl-username (get app-config :repl-username))
    (reset! repl-password (get app-config :repl-password))
    app-config))

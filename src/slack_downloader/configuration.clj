(ns slack-downloader.configuration
  (:require
   [clojure.edn :as edn]
   [environ.core :refer [env]]
   [slack-downloader.storage :refer [storage-init]]))

(def api-port (atom 8990))
(def webhook-url (atom ""))
(def token (atom ""))
(def repl-username (atom "repl"))
(def repl-password (atom "repl"))

(defn config-from-env []
  {:api-port (or (env :api-port) 80)
   :webhook-url (or (env :webhook-url) "")
   :token (or (env :token) "")
   :repl-username (or (env :repl-user) "repl")
   :repl-password (or (env :repl-pass) "repl")})

(defn config []
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

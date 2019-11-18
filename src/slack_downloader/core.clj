(ns slack-downloader.core
  (:require
   [clojure.tools.logging :as log]
   [ring.adapter.jetty :as jetty]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
   [buddy.auth :refer [authenticated?  throw-unauthorized]]
   [buddy.auth.backends.httpbasic :refer [http-basic-backend]]
   [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
   [clojure.edn :as edn]
   [toucan.db :as db]
   [toucan.models :as models]
   [environ.core :refer [env]]
   [java-time :exclude [range iterate format max min] :as time]
   [drawbridge.core])
  (:import (java.sql Timestamp)
           (com.github.seratch.jslack Slack)
           (com.github.seratch.jslack.api.webhook Payload
                                                  WebhookResponse)
           (com.github.seratch.jslack.api.methods MethodsClient)
           (com.github.seratch.jslack.api.methods.request.channels ChannelsListRequest
                                                                   ChannelsHistoryRequest))
  (:gen-class))

(def api-port (atom 8990))
(def webhook-url (atom ""))
(def token (atom ""))
(def repl-username (atom "repl"))
(def repl-password (atom "repl"))

(models/defmodel Message :messages)

(defn slack-ts->timestamp [ts]
  (Timestamp. (long (* 1000 (Double. ts)))))

(defn post-message [msg]
  (let [slack (Slack/getInstance)
        payload (doto (Payload/builder)
                  (.text msg))]
    (.send slack (deref webhook-url) (.build payload))))

(defn channels []
  (let [slack (Slack/getInstance)
        req (doto (ChannelsListRequest/builder)
              (.limit (int 50))
              (.excludeArchived true))]
    (apply merge (map #(hash-map (.getNameNormalized %) (.getId %))
                      (.getChannels (.channelsList (.methods slack (deref token))
                                                   (.build req)))))))

(defn channel-history [channel req]
  (let [slack (Slack/getInstance)]
    (map #(hash-map
           :ts (slack-ts->timestamp (.getTs %)),
           :slack-ts (.getTs %),
           :thread-ts (.getThreadTs %),
           :user (.getUser %),
           :channel channel,
           :text (.getText %))
         (.getMessages (.channelsHistory (.methods slack (deref token))
                                         (.build req))))))

(defn channel-history-after [channel ts]
  (let [req (doto (ChannelsHistoryRequest/builder)
              (.channel channel)
              (.oldest ts)
              (.inclusive false))]
    (channel-history channel req)))

(defn channel-history-for [channel]
  (let [req (doto (ChannelsHistoryRequest/builder)
              (.channel channel))]
    (channel-history channel req)))

(defn most-recent-snapshot [channel]
  (let [entry (db/select-one Message {:where [:= :channel channel]
                                      :order-by [[:ts :desc]]})]
    (hash-map
     :ts (get entry :ts)
     :slack-ts (get entry :slackts))))

(defn snapshot-channel [channel]
  (let [last-snapshot (get (most-recent-snapshot channel) :slack-ts)
        history (channel-history-after channel last-snapshot)]
    (map #(db/insert! Message,
            :ts (get % :ts)
            :slackts (get % :slack-ts)
            :threadts (get % :slack-ts)
            :uid (get % :user)
            :channel channel
            :message (get % :text))
         history)))

(defn snapshot-all-channels []
  (let [ch (channels)]
    (map #(snapshot-channel (get ch %)) (keys ch))))

(defn snapshot-handler [req]
  )

(defn repl-authenticated? [req {:keys [username password]}]
  (and (= username @repl-username)
       (= password @repl-password)
       {:username username}))

(def auth-backend
  (http-basic-backend {:relm "Herodotus"
                       :authfn repl-authenticated?}))

(defroutes herodotus-routes
  (GET "/" [] "Welcome to your friendly Slack historian.")
  (POST "/snapshot" [] "Snapshot"))

(defroutes herodotus-repl-route
  (let [nrepl-handler (drawbridge.core/ring-handler)]
    (ANY "/repl" request (if-not (authenticated? request)
                           (throw-unauthorized)
                           (nrepl-handler request)))))

(def herodotus
  (wrap-defaults
   (routes
    herodotus-routes
    (-> herodotus-repl-route
        (wrap-authorization auth-backend)
        (wrap-authentication auth-backend))) api-defaults))

(defn config-from-env []
  {:api-port (or (env :api-port) 80)
   :webhook-url (or (env :webhook-url) "")
   :token (or (env :token) "")
   :repl-username (or (env :repl-user) "repl")
   :repl-password (or (env :repl-pass) "repl")})

(defn config []
  (merge (config-from-env)
         (edn/read-string (try (slurp "config.edn") (catch Throwable e "{}")))))

(defn init-app []
  (db/set-default-db-connection!
   {:classname   "org.postgresql.Driver"
    :subprotocol "postgresql"
    :subname     "//localhost:5432/herodotus"
    :user        "herodotus"})

  (let [app-config (config)]
    (reset! api-port (get app-config :api-port))
    (reset! webhook-url (get app-config :webhook-url))
    (reset! token (get app-config :token))
    (reset! repl-username (get app-config :repl-username))
    (reset! repl-password (get app-config :repl-password))
    app-config))

(defn -main
  "Initialise config and then start the server."
  [& args]
  (init-app)
  (jetty/run-jetty herodotus {:port (deref api-port)}))

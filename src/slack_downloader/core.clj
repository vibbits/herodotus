(ns slack-downloader.core
  (:use ring.adapter.jetty)
  (:require
   [clojure.edn :as edn]
   [toucan.db :as db]
   [toucan.models :as models]
   [environ.core :refer [env]]
   [java-time :exclude [range iterate format max min] :as time])
  (:import (java.sql Timestamp)
           (com.github.seratch.jslack Slack)
           (com.github.seratch.jslack.api.webhook Payload
                                                  WebhookResponse)
           (com.github.seratch.jslack.api.methods MethodsClient)
           (com.github.seratch.jslack.api.methods.request.channels ChannelsListRequest
                                                                   ChannelsHistoryRequest))
  (:gen-class))

(def webhook-url (atom ""))
(def token (atom ""))

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

(defn handler [req]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (str "<h1>The requested uri is " (get req :uri) " </h1>")})

(defn config-from-env []
  {:webhook-url (or (env :webhook-url) "")
   :token (or (env :token) "")})

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
    (reset! webhook-url (get app-config :webhook-url))
    (reset! token (get app-config :token))))

(defn -main
  "Initialise config and server."
  [& args]
  (init-app)
  (run-jetty handler {:port 8990}))

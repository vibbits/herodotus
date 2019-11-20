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
   [java-time :exclude [range iterate format max min] :as time]
   [drawbridge.core]

   [slack-downloader.storage :as store]
   [slack-downloader.configuration :as config])
  (:import
   (com.github.seratch.jslack Slack)
   (com.github.seratch.jslack.api.webhook Payload
                                          WebhookResponse)
   (com.github.seratch.jslack.api.methods MethodsClient)
   (com.github.seratch.jslack.api.methods.request.channels ChannelsListRequest
                                                           ChannelsHistoryRequest))
  (:gen-class))

(def snapshot-requests (atom []))

(defn post-message
  "Send a message to the #Herodotus channel."
  [msg]
  (let [slack (Slack/getInstance)
        payload (doto (Payload/builder)
                  (.text msg))]
    (.send slack @config/webhook-url (.build payload))))

(defn channels
  "Get a mapping from public channel names to identifiers."
  []
  (let [slack (Slack/getInstance)
        req (doto (ChannelsListRequest/builder)
              (.limit (int 50))
              (.excludeArchived true))]
    (apply merge (map #(hash-map (.getNameNormalized %) (.getId %))
                      (.getChannels (.channelsList (.methods slack @config/token)
                                                   (.build req)))))))

(defn channel-history
  "Execute a channel history request. <req> should be a request builder."
  [channel req]
  (let [slack (Slack/getInstance)]
    (map #(hash-map
           :ts (store/slack-ts->timestamp (.getTs %)),
           :slack-ts (.getTs %),
           :thread-ts (.getThreadTs %),
           :user (.getUser %),
           :channel channel,
           :text (.getText %))
         (.getMessages (.channelsHistory (.methods slack @config/token)
                                         (.build req))))))

(defn channel-history-after
  "Get all messages to a given channel after (not-inclusive) a given timestamp."
  [channel ts]
  (let [req (doto (ChannelsHistoryRequest/builder)
              (.channel channel)
              (.oldest ts)
              (.inclusive false))]
    (channel-history channel req)))

(defn channel-history-for
  "Get the entire history of a given channel."
  [channel]
  (let [req (doto (ChannelsHistoryRequest/builder)
              (.channel channel))]
    (channel-history channel req)))

(defn snapshot-channel
  "Create a snapshot for a given channel."
  [channel]
  (let [last-snapshot (get (store/most-recent-snapshot channel) :slack-ts)
        history (channel-history-after channel last-snapshot)]
    (map store/message history)))

(defn snapshot-all-channels
  "Create a snapshot for all public channels."
  []
  (let [ch (channels)]
    (map #(snapshot-channel (get ch %)) (keys ch))))

(defn welcome
  "A welcoming message for web browsers."
  [req]
  "Welcome to your friendly Slack historian.")

(defn cmd-snapshot-handler
  "HTTP API handler function for the slack /snapshot slash-command."
  [req]
  (swap! snapshot-requests conj req)
  (let [channel-name (:text (:params req))
        channel (get (channels) channel-name)]
    (cond
      (= "all" channel-name) (let [size (count (snapshot-all-channels))]
                               {:status 200,
                                :body (str "I created a snapshot of all " size " channels.")})
      (nil? channel) {:status 404, :body (str "No channel " channel-name)}
      :else (let [size (count (snapshot-channel channel))]
              {:status 200,
               :body (str "I created a snapshot of " channel-name " containing " size " messages.")}))))

(defn repl-authenticated?
  "Check user authentication."
  [req {:keys [username password]}]
  (and (= username @config/repl-username)
       (= password @config/repl-password)
       {:username username}))

(def auth-backend
  (http-basic-backend {:relm "Herodotus"
                       :authfn repl-authenticated?}))

(defroutes herodotus-routes
  (GET "/" [] welcome)
  (POST "/snapshot" [] cmd-snapshot-handler))

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

(defn -main
  "Initialise config and then start the server."
  [& args]
  (config/config)
  (jetty/run-jetty herodotus {:port @config/api-port}))

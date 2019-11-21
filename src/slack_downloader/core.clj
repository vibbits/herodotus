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
   [drawbridge.core]

   [slack-downloader.storage :as store]
   [slack-downloader.configuration :as config])
  (:import
   (com.github.seratch.jslack Slack)
   (com.github.seratch.jslack.api.webhook Payload
                                          WebhookResponse)
   (com.github.seratch.jslack.api.methods MethodsClient)
   (com.github.seratch.jslack.api.methods.request.channels ChannelsListRequest
                                                           ChannelsHistoryRequest)
   (com.github.seratch.jslack.api.methods.request.users UsersListRequest))
  (:gen-class))

(def snapshot-requests (atom []))
(def message-results (atom []))

(defn post-message
  "Send a message to the #Herodotus channel."
  [msg]
  (let [slack (Slack/getInstance)
        payload (doto (Payload/builder)
                  (.text msg))]
    (.send slack @config/webhook-url (.build payload))))

(defn response->channel
  "Extract channel data from the Java response object."
  [response]
  {:identifier (.getId response)
   :name (.getName response)
   :namenormalized (.getNameNormalized response)
   :created (.getCreated response)
   :creator (.getCreator response)})

(defn channels
  "Get a mapping from public channel names to identifiers."
  []
  (let [slack (Slack/getInstance)
        req (doto (ChannelsListRequest/builder)
              (.limit (int 50))
              (.excludeArchived true))]
    (map response->channel
         (.getChannels (.channelsList (.methods slack @config/token)
                                      (.build req))))))

(defn response->user
  "Extract user data from the Java response object."
  [response]
  {:identifier (.getId response)
   :team (.getTeamId response)
   :name (.getName response)
   :realname (.getRealName response)
   :tz (.getTz response)
   :tzlabel (.getTzLabel response)
   :tzoffset (.getTzOffset response)})

(defn users
  "Get a list of users."
  []
  (let [slack (Slack/getInstance)
        req (doto (UsersListRequest/builder)
              (.limit (int 50)))]
    (map response->user
         (.getMembers (.usersList (.methods slack @config/token)
                                  (.build req))))))

(defn response->attachments
  "Retrieve a vector of attachment alternative text from a message."
  [attachments]
  (map
   #(hash-map
     :identifier (.getId %)
     :fallback (.getFallback %)
     :serviceurl (.getServiceUrl %)
     :servicename (.getServiceName %)
     :serviceicon (.getServiceIcon %)
     :authorname (.getAuthorName %)
     :authorlink (.getAuthorLink %)
     :authoricon (.getAuthorIcon %)
     :fromurl (.getFromUrl %)
     :originalurl (.getOriginalUrl %)
     :title (.getTitle %)
     :titlelink (.getTitleLink %)
     :text (.getText %)
     :imageurl (.getImageUrl %)
     :videohtml (.getVideoHtml %)
     :footer (.getFooter %)
     :ts (.getTs %)
     :filename (.getFilename %)
     :mimetype (.getMimetype %)
     :url (.getUrl %))
   attachments))

(defn response->reactions
  "Retrieve a vector of attachment alternative text from a message."
  [reactions]
  (flatten
   (map #(let [name (.getName %)
               url (.getUrl %)
               users (vec (.getUsers %))
               count (.getCount %)]
           (for [idx (range count)]
             {:user_id (get users idx)
              :name name
              :url url}))
        reactions)))

(defn response->message
  "Extract message data from Java response object."
  [response channel]
  {:ts (store/slack-ts->timestamp (.getTs response)),
   :type (.getType response),
   :subtype (.getSubtype response),
   :attachments (response->attachments (.getAttachments response)),
   :reactions (response->reactions (.getReactions response)),
   :slackts (.getTs response),
   :threadts (.getThreadTs response),
   :sender (.getUser response),
   :team (.getTeam response)
   :channel channel,
   :message (.getText response)
   :botid (.getBotId response)
   :botlink (.getBotLink response)})

(defn channel-history
  "Execute a channel history request. <req> should be a request builder."
  [channel req]
  (let [slack (Slack/getInstance)]
    (map #(response->message % channel)
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
  (let [last-snapshot (get (store/most-recent-snapshot channel) :slackts)
        history (channel-history-after channel last-snapshot)]
    (map store/message history)))

(defn snapshot-all-channels
  "Create a snapshot for all public channels."
  []
  (let [ch (map :identifier (channels))]
    (flatten (map snapshot-channel ch))))

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

(defn init
  "Initialise the application."
  []
  (store/init)
  (config/config)
  (if (= 0 (store/stat "users")) (do
                                   (log/info "Initialising table of users.")
                                   (run! store/user (users))))
  (if (= 0 (store/stat "channels")) (do
                                      (log/info "Initialising table of channels.")
                                      (run! store/channel (channels))))
  (if (= 0 (store/stat "messages"))
    (do
      (log/info "Saving archive of available messages.")
      (doseq [message_count (repeatedly #(count (snapshot-all-channels)))
              :while (not= 0 message_count)]
        (log/info "Saved " message_count))))

  ;; TODO: Set up regular checks for new messages.
  )

(defn -main
  "Initialise config and then start the server."
  [& args]
  (init)
  (jetty/run-jetty herodotus {:port @config/api-port}))

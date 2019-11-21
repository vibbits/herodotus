(ns slack-downloader.storage
  (:require
   [toucan.db :as db]
   [toucan.models :as models]
   [java-time :exclude [range iterate format max min] :as time]

   [slack-downloader.configuration :as config])
  (:import
   (java.sql Timestamp)))

(models/defmodel User :users)
(models/defmodel Channel :channels)
(models/defmodel Message :messages)
(models/defmodel Attachment :attachments)
(models/defmodel Reaction :reactions)

(defn init []
  (db/set-default-db-connection!
   {:classname   "org.postgresql.Driver"
    :subprotocol "postgresql"
    :subname     "//localhost:5432/herodotus"
    :user        "herodotus"}))

(defn slack-ts->timestamp
  "Convert a slack timestamp string to a SQL timestamp."
  [ts]
  (Timestamp. (long (* 1000 (Double. ts)))))

(defn epoch-ms->slack-ts
  "Convert the number of milliseconds since the epoch to a slack timestamp"
  [epoch-ms]
  (str
   (.setScale (BigDecimal. (/ epoch-ms 1000.0))
              6
              BigDecimal/ROUND_DOWN)))

(defn most-recent-snapshot
  "Get the timestamp of the most recent message stored for a given channel."
  [channel]
  (let [channel_id (:id (db/select-one Channel, {:where [:= :identifier channel]}))
        entry (db/select-one Message {:where [:= :channel channel_id]
                                      :order-by [[:ts :desc]]})]
    (if (nil? entry)
      (let [beginning (time/to-millis-from-epoch (time/instant @config/beginning-of-history))]
        {:ts      (Timestamp. beginning)
         :slackts (epoch-ms->slack-ts beginning)})
      {:ts      (get entry :ts)
       :slackts (get entry :slackts)})))

(defn attachment
  "Save an attachment to the database."
  [at message_id]
  (let [a (assoc at :message_id message_id)]
    (db/insert! Attachment, a)))

(defn reaction
  "Save a reaction to the database."
  [ren message_id]
  (let [user_id (:id (db/select-one User {:where [:= :identifier (:uid ren)]}))
        r (assoc ren :message_id message_id :user_id user_id)]
    (db/insert! Reaction, r)))

(defn message
  "Save a message to the database."
  [message]
  (let [attachments (:attachments message)
        reactions (:reactions message)
        sender (:id (db/select-one User, {:where [:= :identifier (:sender message)]}))
        channel (:id (db/select-one Channel, {:where [:= :identifier (:channel message)]}))
        msg (assoc message
                   :attachments (count attachments)
                   :reactions (count reactions)
                   :sender sender
                   :channel channel)]
    (let [message_id (:id (db/insert! Message, msg))]
      (map #(attachment message_id %) attachments)
      (map #(reaction message_id %) reactions))
    msg))

(defn channel
  "Save a channel to the database."
  [channel]
  (let [creator (:id (db/select-one User, {:where [:= :identifier (:creator channel)]}))
        created (Timestamp. (* 1000 (:created channel)))
        chnl (assoc channel :created created :creator creator)]
    (db/insert! Channel chnl)))

(defn user
  "Save a user to the database."
  [u]
  (db/insert! User, u))

(defn stat
  "Gather database statistics."
  ([]
   (+
    (db/count User)
    (db/count Message)))
  ([table]
   (cond
     (= table "users") (db/count User)
     (= table "channels") (db/count Channel)
     (= table "messages") (db/count Message)
     (= table "attachments") (db/count Attachment)
     (= table "reactions") (db/count Reaction))))

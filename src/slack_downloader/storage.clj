(ns slack-downloader.storage
  (:require
   [toucan.db :as db]
   [toucan.models :as models])
  (:import
   (java.sql Timestamp)))

(models/defmodel Message :messages)

(defn storage-init []
  (db/set-default-db-connection!
   {:classname   "org.postgresql.Driver"
    :subprotocol "postgresql"
    :subname     "//localhost:5432/herodotus"
    :user        "herodotus"}))

(defn slack-ts->timestamp
  "Convert a slack timestamp string to a SQL timestamp."
  [ts]
  (Timestamp. (long (* 1000 (Double. ts)))))

(defn most-recent-snapshot
  "Get the timestamp of the most recent message stored for a given channel."
  [channel]
  (let [entry (db/select-one Message {:where [:= :channel channel]
                                      :order-by [[:ts :desc]]})]
    {:ts       (get entry :ts),
     :slack-ts (get entry :slackts)}))

(defn message
  "Save a message to the database."
  [message]
  (db/insert! Message,
    :ts (get message :ts)
    :slackts (get message :slack-ts)
    :threadts (get message :slack-ts)
    :uid (get message :user)
    :channel (get message :channel)
    :message (get message :text)))

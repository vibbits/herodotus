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

(defn slack-ts->timestamp [ts]
  (Timestamp. (long (* 1000 (Double. ts)))))

(defn most-recent-snapshot [channel]
  (let [entry (db/select-one Message {:where [:= :channel channel]
                                      :order-by [[:ts :desc]]})]
    {:ts       (get entry :ts),
     :slack-ts (get entry :slackts)}))

(defn message [message]
  (db/insert! Message,
    :ts (get message :ts)
    :slackts (get message :slack-ts)
    :threadts (get message :slack-ts)
    :uid (get message :user)
    :channel (get message :channel)
    :message (get message :text)))

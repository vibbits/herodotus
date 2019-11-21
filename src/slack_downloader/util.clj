(ns slack-downloader.util
  (:require
   [slack-downloader.core :refer [channels]]))

(defn channel-map
  []
  (into {} (map #(vec ( list (:name %) (:identifier %))) (channels))))

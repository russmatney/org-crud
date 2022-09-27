(ns org-crud.parse
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [organum.core :as organum.core]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-lines
  "Parses a seq of strings as org lines."
  [lines]
  (when (seq lines)
    (reduce #'organum.core/handle-line [(#'organum.core/root)] lines)))

(defn parse-file
  [path]
  (try
    (let [f (-> path fs/absolutize str)]
      (with-open [rdr (io/reader f)]
        (parse-lines (line-seq rdr))))
    (catch Exception ex
      (println "org-crud.core/parse-org-file exception" path)
      (println ex)
      nil)))

(comment
  (parse-file (str (fs/home) "/todo/readme.org"))
  (parse-file (str (fs/home) "/todo/daily/2022-09-26.org"))
  (parse-file (str (fs/home) "/todo/journal.org")))

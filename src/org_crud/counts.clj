(ns org-crud.counts
  (:require
   [babashka.fs :as fs]
   [org-crud.core :as core]))

(defn ->report-data [f]
  ;; NOTE this is only level-one totals/averages, not for the whole nested item
  ;; TODO improve reporting here - should not have expectations around structure
  (let [name               (fs/file-name f)
        items              (->> (core/path->nested-item f) :org/items)
        total              (count items)
        word-count         (when (seq items) (reduce + 0 (map :org/word-count items)))
        average-word-count (when (seq items) (int (/ word-count total)))]
    {:report/name               name
     :report/total              total
     :report/word-count         word-count
     :report/average-word-count average-word-count}))

(comment
  (->report-data "/home/russ/todo/prompts.org"))

(defn org-files
  "Fetches org-files sorted in last-modified order."
  []
  (let [org-dir-path "/home/russ/todo"]
    (->> org-dir-path
         fs/list-dir
         (mapcat (fn [f]
                   (if (fs/directory? f)
                     (fs/list-dir f)
                     [f])))
         (remove fs/directory?)
         (filter (comp #{"org"} fs/extension)))))

(comment
  (count (org-files)))

(defn print-report [& _args]
  ;; TODO make sort, take n, org-src-dir parameterizable for callers
  (println "counts" _args)
  (doall
    (->> (org-files)
         (sort-by #(.lastModified %) >)
         (take 20)
         (map ->report-data)
         (map #(do (println %)
                   %)))))

(comment
  (print-report))

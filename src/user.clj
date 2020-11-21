(ns user
  (:require [org-crud.update :as upd]
            [org-crud.core :as org]
            [wing.repl :as w.repl]
            ))


(defn ensure-id [item]
  (let [id
        (or (:id item)
            (-> item :props :id)
            (str (java.util.UUID/randomUUID)))]
    (-> item
        (assoc :id id)
        (assoc-in [:props :id] id))))

(comment
  (w.repl/sync-libs! :dev)

  (def --roam-dir "/home/russ/Dropbox/notes")

  (org/dir->nested-items --roam-dir)
  (upd/update-dir-with-fn! {:dir        --roam-dir
                            :recursive? true} ensure-id)


  (defn missing-id [item]
    (and (not (:id item))
         (not (-> item :props :id))))

  (upd/update-dir-with-fn!
    {:dir --roam-dir}
    (fn [item]
      (when (missing-id item)
        (println "missing id!" (:source-file item))
        (let [id (str (java.util.UUID/randomUUID))]
          {:id id :props {:id id}}))))

  )

(ns org-crud.refile
  (:require
   [org-crud.create :as cr]
   [org-crud.delete :as de]
   ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Refile items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn refile-within-file! [path item context]
  (println "Refiling item within file"
           {:file path :item item :context context})
  (de/delete-from-file! path item)
  (cr/add-to-file! path item context))

(defn refile! [item context]
  (println "Refiling item to context" {:item item :context context})
  (de/delete-item! item)
  (cr/add-item! item context))

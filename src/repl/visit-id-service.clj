(ns #^{:doc "Simple visit id generating web service"
       :author "Kevin A. Archie <karchie@wustl.edu>"}
  nrg.id.visit
  (:use compojure.core
	clojure.test)
  (:require [compojure.route :as route]
	    [compojure.handler :as handler]
	    [hiccup.core :as h]))

(def ^:dynamic *visit-ids* (ref {}))

(defn get-unused-id
  "Find the first unused id for the given id, drawing
indices from the given sequence. Default format produces
ids v000, v001, v002, ..."
  [m & {:keys [id-format index-seq]
	:or {id-format "v%03d"
	     index-seq (range)}}]
  (loop [ids (apply hash-set (vals m))
	 index-seq index-seq]
    (let [id (format id-format (first index-seq))]
      (if (contains? ids id)
	(recur ids (rest index-seq))
	id))))

(deftest get-unused-id-test
  (is (= "v000" (get-unused-id {})))
  (is (= "v000" (get-unused-id {:a "v001"})))
  (is (= "v001" (get-unused-id {:a "v000"})))
  (is (= "v002" (get-unused-id {:a "v000" :b "v001"}))))
      

(defn params->id [{id :id date :date}]
  "Given the query parameters id and date in the parameter map,
return a visit ID."
  (dosync
   (if-let [visit (get-in @*visit-ids* [id date])]
     visit
     (let [visit (get-unused-id (get @*visit-ids* id))]
       (alter *visit-ids* assoc-in [id date] visit)
       visit))))

(deftest params->id-test
  (binding [*visit-ids* (ref {})]
    (is (= "v000" (params->id {:id "s01" :date "20110810"})))
    (is (= "v000" (params->id {:id "s01" :date "20110810"})))
    (is (= "v000" (params->id {:id "s02" :date "20110811"})))
    (is (= "v001" (params->id {:id "s01" :date "20110811"})))
    (is (= "v000" (params->id {:id "s02" :date "20110811"})))
    (is (= "v001" (params->id {:id "s02" :date "20110812"})))
    (is (= "v002" (params->id {:id "s01" :date "20110812"})))
    (is (= "v000" (params->id {:id "s01" :date "20110810"})))))

(defroutes main-routes
  (GET "/services/visitID" {params :params}
       {:status 200
	:header {"Content-Type" "text/plain"}
	:body (params->id params)})
  (GET "/" []
       (h/html [:p "Access via "
		[:a {:href "services/visitID?id=s01&date=20110822"}
		 "services/visitID?id={subject ID}&date={visit date}"]]))
  (route/resources "/")
  (route/not-found "Page not found"))

(def app
     (handler/site main-routes))

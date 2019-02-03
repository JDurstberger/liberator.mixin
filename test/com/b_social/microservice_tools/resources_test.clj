(ns com.b-social.microservice-tools.resources-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as ring]
            [com.b-social.microservice-tools.json :as json]
            [com.b-social.microservice-tools.liberator :as l]
            [com.b-social.microservice-tools.resources :as r]))

(defn call-resource [resource request]
  (->
    (resource request)
    (update :body json/wire-json->map)))

(deftest resources
  (testing "with-routes-in-context"
    (testing "adds routes to the context"
      (let [routes [["/" :root]]
            resource (l/build-resource
                       (r/with-routes-in-context routes)
                       {:handle-ok
                        (fn [{:keys [routes]}]
                          routes)})
            response (call-resource
                       resource
                       (ring/request :get "/"))]
        (is (some? (:body response))))))

  (testing "with-hal-media-type"
    (testing "allows hypermedia requests"
      (let [resource (l/build-resource
                       (r/with-hal-media-type)
                       {:handle-ok (constantly {:status "OK"})})
            response (call-resource
                       resource
                       (ring/header
                         (ring/request :get "/")
                         :accept r/hal-media-type))]
        (is (= 200 (:status response)))
        (is (= {:status "OK"}
              (:body response))))))

  (testing "with-body-parsed-as-json"
    (testing "parses the body as json"
      (let [resource (l/build-resource
                       (r/with-json-media-type)
                       (r/with-body-parsed-as-json)
                       {:allowed-methods [:post]
                        :handle-created
                                         (fn [{:keys [request]}]
                                           (:body request))})
            request (->
                      (ring/request :post "/")
                      (ring/header "Accept" r/json-media-type)
                      (ring/header "Content-Type" r/json-media-type)
                      (ring/body (json/map->wire-json {:key "value"})))
            response (call-resource resource request)]
        (is (= 201 (:status response)))
        (is (=
              {:key "value"}
              (:body response)))))

    (testing "returns a malformed status when it is not valid json"
      (let [resource (l/build-resource
                       (r/with-json-media-type)
                       (r/with-body-parsed-as-json)
                       {:allowed-methods [:post]})
            request (->
                      (ring/request :post "/")
                      (ring/header "Accept" r/json-media-type)
                      (ring/header "Content-Type" r/json-media-type)
                      (ring/body "not valid json"))
            response (resource request)]
        (is (= 400 (:status response))))))

  (testing "with-not-found-handler"
    (testing "provides a sensible default when the resource does not exist"
      (let [resource (l/build-resource
                       (r/with-hal-media-type)
                       (r/with-not-found-handler)
                       {:exists? (constantly false)})
            response (call-resource
                       resource
                       (ring/header
                         (ring/request :get "/")
                         :accept r/hal-media-type))]
        (is (= 404 (:status response)))
        (is (= "Resource not found"
              (get-in response [:body :error])))))))

import * as angular from 'angular';
'use strict';

angular
    .module('flairbiApp')
    .factory('JhiMetricsService', JhiMetricsService);

JhiMetricsService.$inject = ['$rootScope', '$http'];

function JhiMetricsService($rootScope, $http) {
    var service = {
        getMetrics: getMetrics,
        threadDump: threadDump
    };

    return service;

    function getMetrics() {
        return $http.get('management/metrics').then(function (response) {
            return response.data;
        });
    }

    function threadDump() {
        return $http.get('management/dump').then(function (response) {
            return response.data;
        });
    }
}
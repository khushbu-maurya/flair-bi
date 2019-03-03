'use strict';

describe('Service Tests', function () {
    beforeEach(mockApiAccountCall);
    beforeEach(mockI18nCalls);
    beforeEach(mockScriptsCalls);

    describe('Auth', function () {
        var $httpBackend, localStorageService, sessionStorageService, authService, spiedAuthServerProvider;

        beforeEach(inject(function ($injector, $localStorage, $sessionStorage, Auth, AuthServerProvider) {
            $httpBackend = $injector.get('$httpBackend');
            localStorageService = $localStorage;
            sessionStorageService = $sessionStorage;
            authService = Auth;
            spiedAuthServerProvider = AuthServerProvider;
        }));
        it('should call backend on logout then call authServerProvider.logout', function () {
            //GIVEN
            //Set spy
            spyOn(spiedAuthServerProvider, 'logout').and.callThrough();

            //WHEN
            authService.logout();

            //THEN
            expect(spiedAuthServerProvider.logout).toHaveBeenCalled();
            expect(localStorageService.authenticationToken).toBe(undefined);
            expect(sessionStorageService.authenticationToken).toBe(undefined);
        });
    });
});
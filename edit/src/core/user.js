var config = require('../config.js')(location.hostname),
    d3 = require('d3');

module.exports = function(context) {
    var user = {};

    user.details = function(callback) {
        if (!context.storage.get('github_token')) return callback(new Error('not logged in'));

        var cached = context.storage.get('github_user_details');

        if (cached && cached.when > (+new Date() - 1000 * 60 * 60)) {
            callback(null, cached.data);
        } else {
            context.storage.remove('github_user_details');
            var endpoint = !!config.GithubAPI ? config.GithubAPI + '/api/v3' : 'https://api.github.com';

            d3.json(endpoint + '/user')
                .header('Authorization', 'token ' + context.storage.get('github_token'))
                .on('load', onload)
                .on('error', onerror)
                .get();
        }

        function onload(user) {
            context.storage.set('github_user_details', {
                when: +new Date(),
                data: user
            });
            context.storage.set('github_user', user);
            callback(null, user);
        }

        function onerror() {
            user.logout();
            context.storage.remove('github_user_details');
            callback(new Error('not logged in'));
        }
    };

    user.signXHR = function(xhr) {
        return user.token() ?
            xhr.header('Authorization', 'token ' + user.token()) : xhr;
    };

    user.authenticate = function() {
        window.location.href = (config.GithubAPI || 'https://github.com') + '/login/oauth/authorize?redirect_uri=' + encodeURIComponent(window.location.href) + '&client_id=' +
            config.client_id +
            '&scope=gist,repo,delete_repo';
    };

    user.token = function(callback) {
        return context.storage.get('github_token');
    };

    user.logout = function() {
        context.storage.remove('github_token');
    };

    user.login = function() {
        context.storage.remove('github_token');
    };

    user.waitForAuthentization = function(callback) {
        function killTokenUrl() {
            if (window.location.href.indexOf('?code') !== -1) {
                window.location.href = window.location.href.replace(/[?&]code=[A-Za-z0-9]*/, '');
            }
        }

        if (window.location.search && window.location.search.indexOf('?code') === 0) {
            var code = window.location.search.replace('?code=', '');
            d3.select('.map').classed('loading', true);
            d3.json(config.gatekeeper_url + '/authenticate/' + code)
                .on('load', function(l) {
                    d3.select('.map').classed('loading', false);
                    if (l.token) {
                      context.storage.set('github_token', l.token);
                    }
                    killTokenUrl();
                    callback.call(this);
                })
                .on('error', function() {
                    d3.select('.map').classed('loading', false);
                    alert(context.texts.authFailed);
                    callback.call(this);
                })
                .get();
        } else {
          callback.call(this);
        }
    }

    return user;
};

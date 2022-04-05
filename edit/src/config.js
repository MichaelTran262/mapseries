module.exports = function(hostname) {
  console.log("HOSTNAMEhaha lul: ", hostname);
  if (hostname === 'mapseries.mzk.cz') {
    return {
      client_id: '6c133ed6402352753608',
      gatekeeper_url: 'https://mapseries.mzk.cz/server',
    };
  // Customize these settings for your own development/deployment
  // version of geojson.io.
  } else if (hostname === 'localhostaa') {
    return {
      client_id: 'a191aeb3094412d7a47b',
      gatekeeper_url: 'http://localhost:8080/server',
    };
  } else {
      return {
        client_id: 'bb7bbe70bd1f707125bc',
        gatekeeper_url: 'https://localhostauth.herokuapp.com'
      };
    }
};

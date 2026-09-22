// Copy this file to runtime-config.local.js (which is ignored by Git) before
// creating a trial or release build. The production domain must be HTTPS and
// registered as a legal request domain in the WeChat Mini Program console.
module.exports = {
  apiBaseUrls: {
    develop: 'http://localhost:8080/api',
    trial: 'https://api-staging.example.edu/api',
    release: 'https://api.example.edu/api'
  }
}

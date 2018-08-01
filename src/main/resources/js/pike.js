$.getQueryVariable = function(variable) {
  let values = [];
  if (window.location.search) {
    let query = window.location.search.substring(1);
    let vars = query.split('&');
    for (let i = 0; i < vars.length; i++) {
      let pair = vars[i].split('=');
      if (decodeURIComponent(pair[0]) == variable) {
        values.push(decodeURIComponent(pair[1]));
      }
    }
  }
  return values;
}
function delete_connections(names) {
  names.forEach(element => {
    $.ajax({
      url: '/connection/' + element.name,
      type: 'DELETE',
      statusCode: {
        204: function() {
          console.log('Successfully deleted ' + element.name);
        }
      }
    });
    console.log('Deleting ' + element.name);
  });
  window.location.replace('/connections');
}

function use_connection(conn_name) {
  console.log('Using ' + conn_name);
}

function connection_name_link_formatter(value, row, index, field) {
  return '<a href="/connection/' + value + '">' + value + '</a>';
}
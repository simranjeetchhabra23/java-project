
  const express = require('express')
  const helloWorld= require('./helloworld')
  const app = express()
  
  app.get('/', function (req, res) {
      res.send(helloWorld())
    })


app.listen(3000)




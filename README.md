# Mutual Information service

Based on Heroku's Scala app using Play framework.

I have left the index page as is.

Main use case is to do HTTP GET to

  * `http://<server>/mutual`

for list of available names. And using one of the names HTTP GET

  * `http://<server>/mutual/<variable_name>`

For example locally [http://localhost:9000/mutual/acceptance_testing](http://localhost:9000/mutual/acceptance_testing) should work.


## Running Locally

Make sure you have Play and sbt installed.  Also, install the [Heroku Toolbelt](https://toolbelt.heroku.com/).

You can run the production equivalent "stage" build locally as such (set environment variables in `.env` file):
```sh
sbt compile stage
heroku local
# or on Windows
heroku local web -f Procfile.windows
```

or just use Activator for nice refresh-web-page-to-recompile:
```
activator
run
```

In either case the app should be running on [localhost:9000](http://localhost:9000/).

Note: Environment variable `DATABASE_URL` needs to be set to a Postgres DB. Remember`heroku local` may uses `.env` file, so change the var there. Use what values you have available locally for username, password, and dbname. Like this on Windows:

```
set DATABASE_URL=postgres://<USER>:<PASSWORD>@localhost:5432/<DBNAME>
```


## Deploying to Heroku

```sh
$ heroku create
$ git push heroku master
$ heroku open
```

## Todos
Missing or should be done:

  * get tests working (problem with imports)
    * how to even assert simple stuff like "val - expected < eps" ?
  * using getFile to read CSV, might be britle thus move to inputStream
  * store processed MI and d data to to DB
    * sha of the CSV
    * calculated data
    * if sha matches something in DB, no need to process CSV further
  * consider proper exception handling and protection, now we puke on various simple errors
  * fix "d" - it seems not to be Variation of Information :/


## Extra Documentation

For more information about using Play and Scala on Heroku, see these Dev Center articles:

- [Play and Scala on Heroku](https://devcenter.heroku.com/categories/language-support#scala-and-play)

And more about [Mutual Information](https://en.wikipedia.org/wiki/Mutual_information)

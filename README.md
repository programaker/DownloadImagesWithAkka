# DownloadImagesWithAkka

My first Akka project! Using actors to download images from url's in a txt file in paralell.
Also contains a synchronous downloader to compare performances.

The file containing the image url's is in the project, but the download folder is parameterized.

I didn't like the strategy of creating one download Actor for each image url and kill it
with context.stop() when it completes, but that will do for now. I've tried to reuse download Actors and limit
their amount to a maximum, but I've failed miserably to know exactly when they all completed to notify
the application. Need to study more...

At least I could create purely functional Actors, with no mutable state and no var's =D 

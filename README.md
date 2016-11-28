# Download Images With Akka

My first Akka project! Using actors to download images from url's in a txt file in parallel.
Also contains a synchronous downloader to compare performances.

The file containing the image url's is in the project, but the download folder is parameterized.

It's possible to control de maximum number of download Actors via application parameters.
The Actors created are stored in a pool and managed by a Router Actor. 

The Actors are purely functional, with no mutable state and no var's =D 

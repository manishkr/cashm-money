git pull

sbt docker:publishLocal

docker tag cashm-money:0.1 recash-money:latest

docker-compose up -d

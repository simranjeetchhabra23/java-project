To build the Docker image, make sure you are in the project directory and run the following command:


docker build -t my-java-project .
This command builds an image with the tag my-java-project.

To run the container, execute the following command:

docker run -p 8080:8080 my-java-project
This command maps port 8080 in the container to port 8080 on the host machine and runs the container with the my-java-project image. You should be able to access the running Java application by visiting http://localhost:8080 in your web browser.
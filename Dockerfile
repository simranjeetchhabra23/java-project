# Use the official Node.js image as the base image
FROM node:16-alpine

RUN addgroup -S nonroot \
    && adduser -S nonroot -G nonroot

# Set the working directory inside the container
WORKDIR /app

# Copy the package.json and package-lock.json files into the container
COPY package*.json ./

# Install the dependencies
RUN npm install

# Copy the rest of the application files into the container
RUN ls
COPY src test ./

# Expose port 3000 to the outside world
EXPOSE 3000

USER nonroot

# Start the application
CMD ["npm", "start"]

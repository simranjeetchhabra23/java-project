const request = require('supertest');

const deployedAppUrl = 'http://172.17.0.3:30000'; // Replace with the URL of your deployed application

describe('Deployed Application Smoke Tests', () => {
  test('GET /hello should return "Hello, World!"', async () => {
    const response = await request(deployedAppUrl).get('/');
    expect(response.status).toBe(200);
    expect(response.text).toBe('Hello, World!');
  });

  // Add more smoke test cases as needed
});
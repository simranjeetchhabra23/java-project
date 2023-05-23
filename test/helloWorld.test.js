const helloWorld = require("../src/helloworld");

describe("helloWorld", () => {
  it("should return 'Hello, World!'", () => {
    expect(helloWorld()).toEqual("Hello, World!");
  });
});

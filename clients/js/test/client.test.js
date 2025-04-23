import { expect } from 'chai';
import { Client } from '../src/client.js';

describe('Client', () => {
  it('should be created with server endpoint', () => {
    const client = new Client('http://localhost:8080');
    expect(client).to.be.an.instanceOf(Client);
    expect(client._serverEndpoint).to.equal('http://localhost:8080');
  });
});

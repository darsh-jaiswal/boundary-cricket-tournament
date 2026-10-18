import test from 'node:test';
import assert from 'node:assert/strict';
import {googleEmail,normalizeEmail} from '../server/player-identity.js';
import {verifiedGoogle} from '../web/account.js';

function user(provider='oauth_google',status='verified') {
  return {emailAddresses:[{emailAddress:'Player@Example.com',verification:{status:'verified'}}],
    externalAccounts:[{provider,emailAddress:'player@example.com',verification:{status}}]};
}
test('Google identities work without any phone number',()=>{
  assert.equal(googleEmail(user()),'player@example.com');
  assert.equal(verifiedGoogle({isSignedIn:true,user:user()}),true);
});
test('both SDK Google provider names are accepted',()=>{
  assert.equal(googleEmail(user('google')),'player@example.com');
});
test('email-only and other social providers cannot bypass Google registration',()=>{
  for(const candidate of [{emailAddresses:user().emailAddresses},user('oauth_github'),user('email')]) {
    assert.equal(googleEmail(candidate),'');
    assert.equal(verifiedGoogle({isSignedIn:true,user:candidate}),false);
  }
});
test('unverified Google connections and mismatched email identities are rejected',()=>{
  const mismatch=user();mismatch.externalAccounts[0].emailAddress='other@example.com';
  const unverified=user();unverified.emailAddresses[0].verification.status='unverified';
  for(const candidate of [user('oauth_google','unverified'),mismatch,unverified]) {
    assert.equal(googleEmail(candidate),'');
    assert.equal(verifiedGoogle({isSignedIn:true,user:candidate}),false);
  }
});
test('signed-out users and missing accounts are rejected',()=>{
  assert.equal(googleEmail(null),'');
  assert.equal(verifiedGoogle({isSignedIn:false,user:user()}),false);
  assert.equal(verifiedGoogle(null),false);
});
test('email lookup normalizes case and whitespace without changing Gmail aliases',()=>{
  assert.equal(normalizeEmail(' Player+team@Example.COM '),'player+team@example.com');
  for(const invalid of [null,{},'+919876543210','user_123','bad@','a b@example.com','a'.repeat(255)+'@example.com'])
    assert.equal(normalizeEmail(invalid),'');
});

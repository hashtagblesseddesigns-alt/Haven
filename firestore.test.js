const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const ADMIN_EMAIL = "josheezymusic@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).collection("bookings").get()
  );
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).collection("bookings").doc("b1").set({
      userId: ALICE_UID,
      companionId: "elena_01",
      companionName: "Elena Vance",
      sessionTitle: "Mindful Tea & Presence",
      durationMinutes: 60,
      currency: "USD",
      grossCentsUsd: 12000,
      taxWithheldCentsUsd: 2400,
      status: "CONFIRMED",
      scheduledLabel: "Today 5:00 PM",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner UserProfile: valid Kotlin create & update succeed; ghost fields & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice M.",
      role: "CLIENT",
      verificationStatus: "STANDARD",
      preferredCurrency: "USD",
      wellnessFocus: "Quiet conversation & breathwork",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Shadow update / ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Valid partial update including displayName and avatarBase64
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Moreau",
      avatarBase64: "/9j/4AAQSkZJRgABAQAAAQABAAD/",
      verificationStatus: "VERIFIED_2FA",
      preferredCurrency: "EUR",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Oversized avatarBase64 (> 80,000 chars) rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      avatarBase64: "A".repeat(80001),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user read/write blocked
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("SessionBookings: valid create, status transition, and terminal state locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);
  const bookingsRef = aliceDb.collection("users").doc(ALICE_UID).collection("bookings");

  await assertSucceeds(
    bookingsRef.doc("booking_1").set({
      userId: ALICE_UID,
      companionId: "elena_01",
      companionName: "Elena Vance",
      sessionTitle: "Platonic Cuddle & Calm Reset",
      durationMinutes: 90,
      currency: "USD",
      grossCentsUsd: 18000,
      taxWithheldCentsUsd: 3600,
      status: "CONFIRMED",
      scheduledLabel: "Thu 7:00 PM",
      createdAt: now,
      updatedAt: now,
    })
  );

  // List subcollection matches Kotlin observeBookings(uid)
  await assertSucceeds(bookingsRef.get());

  // Cross-user list fails
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("bookings").get()
  );

  // Transition to PAYOUT_REQUESTED succeeds
  await assertSucceeds(
    bookingsRef.doc("booking_1").update({
      status: "PAYOUT_REQUESTED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: non-admin cannot mutate after PAYOUT_REQUESTED
  await assertFails(
    bookingsRef.doc("booking_1").update({
      status: "CONFIRMED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp tamper
  await assertFails(
    bookingsRef.doc("booking_2").set({
      userId: ALICE_UID,
      companionId: "marcus_02",
      companionName: "Marcus Sterling",
      sessionTitle: "Future Tamper",
      durationMinutes: 60,
      currency: "USD",
      grossCentsUsd: 10000,
      taxWithheldCentsUsd: 2000,
      status: "CONFIRMED",
      scheduledLabel: "Tomorrow",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("EncryptedMessages, Matches, EoaAgentRuns & ActivityLogs: strict owner & schema validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 3000);

  // MatchScore
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("matches").doc("elena_01").set({
      userId: ALICE_UID,
      companionId: "elena_01",
      companionName: "Elena Vance",
      compatibilityScore: 96,
      rationale: "Aligned evening mindfulness & quiet acoustic sanctuary preference.",
      recommendedFormat: "90m Cozy Reading & Tea",
      bookmarked: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // EncryptedMessage (no `id` field!)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      companionId: "elena_01",
      companionName: "Elena Vance",
      senderRole: "USER",
      cipherText: "U2FsdGVkX1+AESGCMBASE64==",
      ivBase64: "MTIzNDU2Nzg5MDEy",
      keyFingerprint: "SHA256:9F4A21B8",
      createdAt: now,
      updatedAt: now,
    })
  );

  // EoaAgentRun
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("agentRuns").doc("run_1").set({
      userId: ALICE_UID,
      directive: "Full mesh latency & fraud audit",
      eoaSummary: "All 5 sub-agents operating within 99.98% SLA.",
      overallHealthScore: 98,
      fraudRiskLevel: "LOW",
      activeAlertsCount: 0,
      matchmakerScore: 97,
      fraudSentinelScore: 99,
      taxAgentScore: 96,
      cryptoGuardianScore: 100,
      infraBalancerScore: 98,
      recommendedAction: "Keep auto-scaling warm standby pool at 2 pods.",
      latencyMs: 142,
      createdAt: now,
      updatedAt: now,
    })
  );

  // ActivityLog (immutable to non-admin)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("activityLogs").doc("log_1").set({
      userId: ALICE_UID,
      category: "CIPHER",
      title: "AES-256-GCM Session Key Rotated",
      detail: "Key fingerprint SHA256:9F4A21B8 verified.",
      severity: "INFO",
      createdAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("activityLogs").doc("log_1").update({
      severity: "CRITICAL",
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("activityLogs").get()
  );
});

test("Admin email spoofing test: unverified admin email rejected, verified admin email allowed", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const now = new Date(Date.now() - 5000);
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      role: "CLIENT",
      verificationStatus: "STANDARD",
      preferredCurrency: "USD",
      createdAt: now,
      updatedAt: now,
    });
  });

  // Unverified spoofed email must FAIL
  const spoofAdmin = testEnv
    .authenticatedContext("spoof_uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();
  await assertFails(spoofAdmin.collection("users").doc(ALICE_UID).get());

  // Verified admin email SUCCEEDS
  const realAdmin = testEnv
    .authenticatedContext("real_admin_uid", {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
  await assertSucceeds(realAdmin.collection("users").doc(ALICE_UID).get());
});

test("LiveChats: real-time chat create, list, text bounds, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);
  const chatRef = aliceDb.collection("users").doc(ALICE_UID).collection("liveChats");

  await assertSucceeds(
    chatRef.doc("chat_1").set({
      userId: ALICE_UID,
      companionId: "elena_01",
      companionName: "Elena Vance",
      senderRole: "USER",
      senderName: "Alice Moreau",
      text: "Hi Elena, looking forward to our tea & breathwork session!",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(chatRef.get());

  // Empty text must fail
  await assertFails(
    chatRef.doc("chat_2").set({
      userId: ALICE_UID,
      companionId: "elena_01",
      companionName: "Elena Vance",
      senderRole: "USER",
      senderName: "Alice Moreau",
      text: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cross-user read & write must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("liveChats").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("liveChats").doc("chat_3").set({
      userId: ALICE_UID,
      companionId: "elena_01",
      companionName: "Elena Vance",
      senderRole: "COMPANION",
      senderName: "Elena Vance",
      text: "Spoofed message",
      createdAt: now,
      updatedAt: now,
    })
  );
});


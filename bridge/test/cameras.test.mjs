import assert from "node:assert/strict";
import test from "node:test";
import { once } from "node:events";
import { MjpegMultipartTransform } from "../src/cameras.mjs";

test("wraps split JPEG frames as an MJPEG multipart response", async () => {
  const frameOne = Buffer.from([0xff, 0xd8, 0x01, 0x02, 0xff, 0xd9]);
  const frameTwo = Buffer.from([0xff, 0xd8, 0x03, 0xff, 0xd9]);
  const transform = new MjpegMultipartTransform("frame");
  const chunks = [];
  transform.on("data", (chunk) => chunks.push(chunk));
  transform.write(Buffer.concat([Buffer.from("noise"), frameOne.subarray(0, 3)]));
  transform.write(Buffer.concat([frameOne.subarray(3), frameTwo.subarray(0, 2)]));
  transform.end(frameTwo.subarray(2));
  await once(transform, "end");

  const output = Buffer.concat(chunks);
  const text = output.toString("latin1");
  assert.match(text, /--frame\r\nContent-Type: image\/jpeg\r\nContent-Length: 6/);
  assert.match(text, /--frame\r\nContent-Type: image\/jpeg\r\nContent-Length: 5/);
  assert.ok(output.includes(frameOne));
  assert.ok(output.includes(frameTwo));
  assert.ok(text.endsWith("--frame--\r\n"));
});

test("does not emit a fake multipart success when no camera frame arrived", async () => {
  const transform = new MjpegMultipartTransform("frame");
  const chunks = [];
  transform.on("data", (chunk) => chunks.push(chunk));
  transform.end(Buffer.from("camera failed before producing a JPEG"));
  await once(transform, "end");
  assert.equal(Buffer.concat(chunks).length, 0);
});

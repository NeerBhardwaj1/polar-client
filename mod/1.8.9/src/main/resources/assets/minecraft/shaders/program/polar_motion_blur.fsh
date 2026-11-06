#version 120

// Polar Client motion blur: mixes this frame of the world with the frames before it ("previous" keeps the result
// from one frame to the next). Before the first frame "previous" is empty (alpha 0), so nothing old shows through.
uniform sampler2D DiffuseSampler;
uniform sampler2D PrevSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

uniform float BlendFactor;

void main() {
    vec3 current = texture2D(DiffuseSampler, texCoord).rgb;
    vec4 previous = texture2D(PrevSampler, texCoord);
    gl_FragColor = vec4(mix(current, previous.rgb, BlendFactor * previous.a), 1.0);
}

#version 330

// Polar Client motion blur: mixes this frame of the world with the frames before it ("previous" keeps the result
// from one frame to the next). Before the first frame "previous" is empty (alpha 0), so nothing old shows through.
uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

layout(std140) uniform MotionBlurConfig {
    float BlendFactor;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 current = texture(InSampler, texCoord).rgb;
    vec4 previous = texture(PrevSampler, texCoord);
    fragColor = vec4(mix(current, previous.rgb, BlendFactor * previous.a), 1.0);
}

// Isolated research process. JSON lines over stdio; no server, downloader or network API.
// Uses the pinned MIT llama.cpp C API and its vendored MIT nlohmann JSON header.
#include "llama.h"
#include "nlohmann/json.hpp"
#include <chrono>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

using json = nlohmann::json;
using Clock = std::chrono::steady_clock;
static double milliseconds(Clock::time_point since) {
    return std::chrono::duration<double, std::milli>(Clock::now() - since).count();
}

static void tokens(llama_batch_ext *batch, const llama_token *ids, int count, int start) {
    llama_batch_ext_clear(batch);
    for (int i = 0; i < count; ++i) {
        int index = llama_batch_ext_add_token(batch, 0, ids[i]);
        llama_pos position = start + i;
        llama_batch_ext_set_pos(batch, index, &position);
    }
    llama_batch_ext_set_output_logits(batch, count - 1, true);
}

int main(int argc, char **argv) {
    if (argc != 3) return 2;
    llama_backend_init();
    auto started = Clock::now();
    auto model_parameters = llama_model_default_params();
    model_parameters.n_gpu_layers = 0;
    auto *model = llama_model_load_from_file(argv[1], model_parameters);
    if (!model) return 3;
    auto parameters = llama_context_default_params();
    parameters.n_ctx = 2048;
    parameters.n_batch = 2048;
    parameters.n_ubatch = 512;
    parameters.n_threads = std::stoi(argv[2]);
    parameters.n_threads_batch = parameters.n_threads;
    auto *context = llama_init_from_model(model, parameters);
    if (!context) return 4;
    const auto *vocab = llama_model_get_vocab(model);
    auto *batch = llama_batch_ext_init(context);
    std::cout << json({{"ready", true}, {"load_ms", milliseconds(started)},
                       {"context_tokens", parameters.n_ctx}, {"threads", parameters.n_threads},
                       {"parameters", llama_model_n_params(model)}}).dump() << std::endl;
    std::string line;
    while (std::getline(std::cin, line)) {
        try {
            auto request = json::parse(line);
            if (request.contains("shutdown")) break;
            std::string prompt = request.at("prompt");
            int limit = request.value("max_tokens", 128);
            if (limit < 1 || limit > 256) throw std::runtime_error("Invalid generation limit");
            int count = -llama_tokenize(vocab, prompt.data(), (int)prompt.size(), nullptr, 0, true, true);
            if (count < 1 || count + limit >= 2048) throw std::runtime_error("Input exceeds context; no truncation");
            std::vector<llama_token> input(count);
            if (llama_tokenize(vocab, prompt.data(), (int)prompt.size(), input.data(), count, true, true) < 0)
                throw std::runtime_error("Tokenization failed");
            llama_memory_clear(llama_get_memory(context), true);
            auto *sampler = llama_sampler_init_greedy();
            tokens(batch, input.data(), count, 0);
            auto inference_start = Clock::now();
            if (llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch)) {
                llama_sampler_free(sampler);
                throw std::runtime_error("Prompt evaluation failed");
            }
            double prefill_ms = milliseconds(inference_start);
            auto generation_start = Clock::now();
            std::string output;
            bool completed = false;
            int generated = 0;
            for (int i = 0; i < limit; ++i) {
                auto token = llama_sampler_sample(sampler, context, -1);
                if (llama_vocab_is_eog(vocab, token)) { completed = true; break; }
                std::vector<char> piece(256);
                int size = llama_token_to_piece(vocab, token, piece.data(), (int)piece.size(), 0, false);
                if (size < 0) {
                    piece.resize(-size);
                    size = llama_token_to_piece(vocab, token, piece.data(), (int)piece.size(), 0, false);
                }
                if (size < 0) break;
                output.append(piece.data(), size);
                ++generated;
                tokens(batch, &token, 1, count + i);
                if (llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch)) break;
            }
            double generation_ms = milliseconds(generation_start);
            llama_sampler_free(sampler);
            std::cout << json({{"text", output}, {"completed", completed}, {"tokens", generated},
                               {"prompt_tokens", count}, {"prefill_ms", prefill_ms},
                               {"generation_ms", generation_ms}, {"ms", milliseconds(inference_start)},
                               {"tokens_per_second", generation_ms > 0 ? generated * 1000 / generation_ms : 0}}).dump(-1, ' ', false, json::error_handler_t::replace) << std::endl;
        } catch (const std::exception &error) {
            std::cout << json({{"error", error.what()}}).dump() << std::endl;
        }
    }
    llama_batch_ext_free(batch);
    llama_free(context);
    llama_model_free(model);
    llama_backend_free();
}

package com.wantox86.signpdf.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.wantox86.signpdf.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbarLogin.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnContinueAsGuest.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text?.toString().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            viewModel.login(username, password)
        }

        // viewLifecycleOwner.lifecycleScope + repeatOnLifecycle, not the Fragment's own
        // lifecycleScope: same reasoning as HomeFragment -- keeps collectors from piling up
        // across back-stack re-creations of this view.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressLogin.visibility =
                        if (state is LoginUiState.Loading) View.VISIBLE else View.GONE
                    binding.btnLogin.isEnabled = state !is LoginUiState.Loading

                    when (state) {
                        is LoginUiState.Success -> findNavController().popBackStack()
                        is LoginUiState.Error -> Snackbar.make(
                            binding.root, state.message, Snackbar.LENGTH_LONG
                        ).show()
                        else -> Unit
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

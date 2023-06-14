package live.hms.app2.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import live.hms.app2.BuildConfig
import live.hms.app2.R
import live.hms.app2.databinding.FragmentHomeBinding
import live.hms.roomkit.ui.settings.SettingsMode
import live.hms.roomkit.ui.settings.SettingsStore
import live.hms.roomkit.util.EmailUtils
import live.hms.app2.util.*
import live.hms.app2.util.NameUtils.isValidUserName
import live.hms.roomkit.ui.HMSPrebuiltOptions
import live.hms.roomkit.ui.HMSRoomKit
import live.hms.roomkit.ui.meeting.*
import live.hms.roomkit.util.contextSafe

class HomeFragment : Fragment() {

    companion object {
        private const val TAG = "HomeFragment"
    }

    private var binding by viewLifecycle<FragmentHomeBinding>()
    private lateinit var settings: SettingsStore

    override fun onResume() {
        super.onResume()
        val data = requireActivity().intent.data
        Log.v(TAG, "onResume: Trying to update $data into EditTextMeetingUrl")

        data?.let {
            if (it.toString().isNotEmpty()) {
                val url = it.toString()
                requireActivity().intent.data = null
                if (saveTokenEndpointUrlIfValid(url) && isValidUserName(binding.editTextName)) {
                    joinRoom()
                }
            }
        }

        val person = requireActivity().intent.getStringExtra(LEAVE_INFORMATION_PERSON)
        val reason = requireActivity().intent.getStringExtra(LEAVE_INFORMATION_REASON)
        val roomWasEnded =
            requireActivity().intent.getBooleanExtra(LEAVE_INFROMATION_WAS_END_ROOM, false)

        if (person != null && reason != null) {
            requireActivity().intent.removeExtra(LEAVE_INFORMATION_PERSON)
            requireActivity().intent.removeExtra(LEAVE_INFORMATION_REASON)
            requireActivity().intent.removeExtra(LEAVE_INFROMATION_WAS_END_ROOM)
            createForceLeaveDialog(person, reason, roomWasEnded)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> {
                findNavController().navigate(
                    HomeFragmentDirections.actionHomeFragmentToSettingsFragment(SettingsMode.HOME)
                )
            }
            R.id.action_email_logs -> {
                requireContext().startActivity(
                    EmailUtils.getNonFatalLogIntent(requireContext())
                )
            }
            R.id.action_stats -> {
                val deviceStatsBottomSheet = DeviceStatsBottomSheet()
                deviceStatsBottomSheet.show(
                    requireActivity().supportFragmentManager, "deviceStatsBottomSheet"
                )
            }
        }
        return false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        settings = SettingsStore(requireContext())

        setHasOptionsMenu(true)

        initEditTextViews()
        initConnectButton()
        hideProgressBar()

        return binding.root
    }

    @SuppressLint("SetTextI18n")
    private fun updateProgressBarUI() {
        val headingPrefix = "Fetching Token"
        binding.progressBar.heading.text = "$headingPrefix ${getUsername()}..."

        val descriptionDefaults = if (settings.publishVideo && settings.publishAudio) {
            "Video and microphone will be turned on by default.\n"
        } else if (settings.publishVideo && !settings.publishVideo) {
            "Only audio will be turned on by default\n"
        } else if (!settings.publishVideo && settings.publishVideo) {
            "Only video will be turned on by default\n"
        } else {
            "Video and microphone will be turned off by default.\n"
        }

        val descriptionSetting = "You can change the defaults in the app settings."
        binding.progressBar.description.text = descriptionDefaults + descriptionSetting
    }

    private fun showProgressBar() {
        binding.goLiveView.visibility = View.GONE
        binding.progressBar.root.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        binding.goLiveView.visibility = View.VISIBLE
        binding.progressBar.root.visibility = View.GONE
    }

    private fun getUsername() = binding.editTextName.text.toString()

    private fun joinRoom() {
        settings.lastUsedMeetingUrl = settings.lastUsedMeetingUrl.replace("/preview/", "/meeting/")
        getRoomCodeFromURl(settings.lastUsedMeetingUrl)
    }

    private fun getRoomCodeFromURl(url: String) {
        when {
            REGEX_MEETING_URL_CODE.matches(url) -> {
                val groups = REGEX_MEETING_URL_CODE.findAll(url).toList()[0].groupValues
                val code = groups[2]
                launchPrebuilt(code)


            }
            REGEX_STREAMING_MEETING_URL_ROOM_CODE.matches(url) -> {
                val groups =
                    REGEX_STREAMING_MEETING_URL_ROOM_CODE.findAll(url).toList()[0].groupValues
                val code = groups[2]
                launchPrebuilt(code)

            }
            REGEX_PREVIEW_URL_CODE.matches(url) -> {
                val groups = REGEX_PREVIEW_URL_CODE.findAll(url).toList()[0].groupValues
                val code = groups[2]
                launchPrebuilt(code)

            }
        }
    }

    private fun launchPrebuilt(code: String) {
        contextSafe { context, activity ->

            HMSRoomKit.launchPrebuilt(
                code, activity, HMSPrebuiltOptions(userName = getUsername(), userId = "random-user-id",
                    endPoints = hashMapOf<String, String>().apply {
                        if (settings.environment.contains("prod").not()) {
                            put("token", "https://auth-nonprod.100ms.live")
                            put("init", "https://qa-init.100ms.live/init")
                        }
                    })
            )
        }
    }


    private fun saveTokenEndpointUrlIfValid(url: String): Boolean {
        if (url.isValidMeetingUrl()) {
            settings.lastUsedMeetingUrl = url
            settings.environment = url.getInitEndpointEnvironment()
            return true
        }

        return false
    }

    private fun initEditTextViews() {
        // Load the data if saved earlier (easy debugging)
        binding.editTextName.setText(settings.username)

        binding.editTextName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    disableButton()
                } else {
                    enableButton()
                }
            }

            override fun afterTextChanged(s: Editable?) {

            }
        })

    }

    private fun initConnectButton() {

        binding.buttonJoinMeeting.setOnClickListener {
            try {
                val input = (requireActivity() as HomeActivity).meetingUrl
                if (saveTokenEndpointUrlIfValid(input) && isValidUserName(binding.editTextName)) {
                    joinRoom()
                    settings.username = binding.editTextName.text.toString()
                } else if (REGEX_MEETING_CODE.matches(input) && isValidUserName(binding.editTextName)) {
                    var subdomain = BuildConfig.TOKEN_ENDPOINT.toSubdomain()
                    if (BuildConfig.INTERNAL) {
                        val env = when (settings.environment) {
                            ENV_PROD -> "prod2"
                            else -> "qa2"
                        }
                        subdomain = "$env.100ms.live"
                    }
                    val url = "https://$subdomain/meeting/$input"
                    saveTokenEndpointUrlIfValid(url)
                    joinRoom()
                } else {
                    Toast.makeText(requireContext(), "Invalid Meeting URL", Toast.LENGTH_LONG)
                        .show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enableButton() {
        binding.buttonJoinMeeting.isEnabled = true
        binding.buttonJoinMeeting.background =
            ContextCompat.getDrawable(requireContext(), R.drawable.primary_blue_round_drawable)
    }

    private fun disableButton() {
        binding.buttonJoinMeeting.isEnabled = false
        binding.buttonJoinMeeting.background =
            ContextCompat.getDrawable(requireContext(), R.drawable.primary_disabled_round_drawable)

    }

    private fun createForceLeaveDialog(removedBy: String, reason: String, wasRoomEnded: Boolean) {
        val message = if (wasRoomEnded) {
            "The room was ended by ${removedBy}.\nThe reason was $reason."
        } else {
            "You were removed from the room by ${removedBy}.\nThe reason was: $reason."
        }

        val title = if (wasRoomEnded) {
            "Room Ended"
        } else {
            "Removed from the room"
        }

        val builder = AlertDialog.Builder(requireContext()).setMessage(message).setTitle(title)
            .setCancelable(false)

        builder.setPositiveButton(R.string.ok) { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().apply { show() }

    }
}
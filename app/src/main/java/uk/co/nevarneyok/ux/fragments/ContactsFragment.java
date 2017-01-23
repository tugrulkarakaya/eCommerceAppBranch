package uk.co.nevarneyok.ux.fragments;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.icu.text.AlphabeticIndex;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import uk.co.nevarneyok.R;
import uk.co.nevarneyok.SettingsMy;
import uk.co.nevarneyok.controllers.CallingContacts;
import uk.co.nevarneyok.controllers.UserController;
import uk.co.nevarneyok.entities.Contact;
import uk.co.nevarneyok.entities.User;
import uk.co.nevarneyok.ux.MainActivity;

/**
 * A simple {@link Fragment} subclass.
 */
public class ContactsFragment extends Fragment {
    static CallingContacts callingContacts=null;

    Cursor cursor;
    ArrayList<Contact> contactlist = new ArrayList<Contact>();
    int counter;
    private ProgressDialog pDialog;
    private Handler updateBarHandler;

    DatabaseReference myRef=FirebaseDatabase.getInstance().getReference();
    DatabaseReference pushRef;

    DatabaseReference myFirebaseRef;
    private RecyclerView contactsListView;
    User activeUser = SettingsMy.getActiveUser();


    public ContactsFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        MainActivity.setActionBarTitle(getString(R.string.Contact_List));
        View view = inflater.inflate(R.layout.fragment_contacts, container, false);
        contactsListView = (RecyclerView) view.findViewById(R.id.contactlist);
        contactsListView.setLayoutManager(new LinearLayoutManager(getContext()));

        updateBarHandler =new Handler();
        pDialog = new ProgressDialog(getContext());

        if(activeUser!=null){
            callingContacts = new CallingContacts();
            myRef = FirebaseDatabase.getInstance().getReference("contacts").child(activeUser.getUid());
        }
        myFirebaseRef=myRef.child("contacts");



        // Inflate the layout for this fragment
        return view;
    }

    public static class ContactListHolder extends RecyclerView.ViewHolder{
        View mView;
        public ContactListHolder(View itemView) {
            super(itemView);
            mView = itemView;
        }

        public void setName(String name){
            TextView contact_name = (TextView) mView.findViewById(R.id.contact_name);
            contact_name.setText(name);
        }
        public void setPhone(String phone){
            TextView contact_phone = (TextView) mView.findViewById(R.id.contact_phone);
            contact_phone.setText(phone);
        }
        public void setPhoto(String photo){
            ImageView contact_photo = (ImageView) mView.findViewById(R.id.contact_photo);
            if(photo!=null) {
                Picasso.with(mView.getContext()).load(photo).into(contact_photo);
            }else{
                contact_photo.setBackgroundResource(R.drawable.user_black);
            }
        }
        public void setAdd(final String key, final Contact contact){
            ImageView contact_add_remove = (ImageView) mView.findViewById(R.id.contact_add_remove);
            contact_add_remove.setBackgroundResource(R.drawable.ic_add_circle_outline_black_24dp);
            contact_add_remove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    callingContacts.addCallingGroup("friends",key, contact);
                    Toast.makeText(mView.getContext(), "Aranacaklar Listesine Eklendi.",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }



    @Override
    public void onStart() {
        super.onStart();

        FirebaseRecyclerAdapter<Contact, ContactListHolder> firebaseRecyclerAdapter = new FirebaseRecyclerAdapter<Contact, ContactListHolder>(
                Contact.class,
                R.layout.contacts_list_row,
                ContactListHolder.class,
                myFirebaseRef

        ) {
            @Override
            protected void populateViewHolder(ContactListHolder viewHolder, final Contact model, int position) {

                viewHolder.setName(model.getName());
                viewHolder.setPhone(model.getPhone());
                viewHolder.setPhoto(model.getPhotoUrl());
                viewHolder.setAdd(getRef(position).getKey(),model);
            }
        };
        contactsListView.setAdapter(firebaseRecyclerAdapter);

        UserController userController;
        if(activeUser != null){
            callingContacts.existsData(new CallingContacts.completion() {
                @Override
                public void setResult(boolean result) {
                    if(!result){
                        pDialog.setMessage("Reading contacts...");
                        pDialog.setCancelable(false);
                        pDialog.show();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                getContacts();

                            }
                        }).start();
                    }
                }
            });
        }



    }

    public void getContacts() {

        String phoneNumber = null;
        Uri CONTENT_URI = ContactsContract.Contacts.CONTENT_URI;
        String _ID = ContactsContract.Contacts._ID;
        String DISPLAY_NAME = ContactsContract.Contacts.DISPLAY_NAME;
        String HAS_PHONE_NUMBER = ContactsContract.Contacts.HAS_PHONE_NUMBER;
        Uri PhoneCONTENT_URI = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String Phone_CONTACT_ID = ContactsContract.CommonDataKinds.Phone.CONTACT_ID;
        String NUMBER = ContactsContract.CommonDataKinds.Phone.NUMBER;

        Contact contact;
        ContentResolver contentResolver = getContext().getContentResolver();
        cursor = contentResolver.query(CONTENT_URI, null,null, null, null);
        // Iterate every contact in the phone
        if (cursor.getCount() > 0) {
            counter = 0;
            while (cursor.moveToNext()) {
                // Update the progress message
                updateBarHandler.post(new Runnable() {
                    public void run() {
                        pDialog.setMessage("Reading contacts : "+ counter++ +"/"+cursor.getCount());
                    }
                });
                String contact_id = cursor.getString(cursor.getColumnIndex( _ID ));
                String name = cursor.getString(cursor.getColumnIndex( DISPLAY_NAME ));
                int hasPhoneNumber = Integer.parseInt(cursor.getString(cursor.getColumnIndex( HAS_PHONE_NUMBER )));
                if (hasPhoneNumber > 0) {

                    //This is to read multiple phone numbers associated with the same contact
                    Cursor phoneCursor = contentResolver.query(PhoneCONTENT_URI, null, Phone_CONTACT_ID + " = ?", new String[] { contact_id }, null);

                    while (phoneCursor.moveToNext()) {
                        contact = new Contact();
                        contact.setName(name);

                        int phoneType = phoneCursor.getInt(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE));
                        phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(NUMBER));
                        phoneNumber = phoneNumber.replace(" ","");
                        if(phoneType== ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE){
                            contact.setPhone(phoneNumber);
                        }
                        if (!(contact.getName() ==null)&& !(contact.getPhone()==null)){
                            contactlist.add(contact);
                        }
                    }
                    phoneCursor.close();
                }
                // Add the contact to the ArrayList



            }
            // ListView has to be updated using a ui thread
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Collections.sort(contactlist, new Comparator<Contact>() {
                        @Override
                        public int compare(Contact contact, Contact t1) {
                            Collator trCollator = Collator.getInstance(new Locale("tr", "TR"));
                            return trCollator.compare(contact.getName(),t1.getName());
                        }
                    });
                    for (int i=0; i<contactlist.size();i++){
                        for (int j=i+1; j<contactlist.size();j++){
                            if(contactlist.get(i).getName().equals(contactlist.get(j).getName()) &&
                                    contactlist.get(i).getPhone().equals(contactlist.get(j).getPhone())){
                                contactlist.remove(j);
                                j--;
                            }
                        }
                    }
                    for(Contact contact : contactlist){
                        pushRef = myRef.child("contacts").push();
                        pushRef.setValue(contact);
                    }

                }
            });
            // Dismiss the progressbar after 500 millisecondds
            updateBarHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    pDialog.cancel();
                }
            }, 500);
        }
    }

}
